// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.IntroductionActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.databinding.FragmentInternetPermissionBinding
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.anki.startup.resetAnkiDroidDirectory
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R], shadows = [PermissionsActivityTaskReplacementTest.TaskRestartShadow::class])
class PermissionsActivityTaskReplacementTest : RobolectricTest() {
    @Implements(Activity::class)
    class TaskRestartShadow : ShadowActivity() {
        @Implementation
        protected fun startActivityIfNeeded(
            intent: Intent,
            requestCode: Int,
        ): Boolean {
            require(requestCode == 0)
            restart = Intent(intent)
            return true
        }

        companion object {
            var restart: Intent? = null
        }
    }

    @Test
    fun `fresh app folder setup completes when a share intent replaces its task`() = checkTaskReplacement()

    @Test
    fun `fresh app folder setup completed while replacement is stopped still requires Internet`() =
        checkTaskReplacement(missingInternetWhileStopped = true)

    @Test
    fun `fresh public choice supersedes the destroyed task private choice`() = checkTaskReplacement(restoringPublicAccess = true)

    @Test
    fun `startup without public access preserves the pending fresh private choice`() =
        checkTaskReplacement(retryStartupWithoutPermissions = true)

    private fun checkTaskReplacement(
        missingInternetWhileStopped: Boolean = false,
        restoringPublicAccess: Boolean = false,
        retryStartupWithoutPermissions: Boolean = false,
    ) {
        targetContext.sharedPrefs().edit {
            putBoolean(IntroductionActivity.PREF_INTRODUCTION_SLIDES_SHOWN, true)
            remove(CollectionHelper.PREF_COLLECTION_PATH)
        }
        // The original DeckPicker has already loaded backend translations before launching permissions.
        CollectionManager.getBackend()
        val workerPaused = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val previous = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        mockkStatic(::resetAnkiDroidDirectory)
        mockkObject(CollectionHelper)
        every { CollectionHelper.initializeAnkiDroidDirectory(any()) } answers {
            callOriginal()
            // Pause filesystem work after consent was checked, before committing the path.
            workerPaused.countDown()
            check(releaseWorker.await(30, TimeUnit.SECONDS))
        }
        try {
            withManageExternalStorageInManifest {
                val original =
                    Robolectric.buildActivity(
                        PermissionsActivity::class.java,
                        PermissionsActivity.getIntent(targetContext, selectStoragePermissions(targetContext)),
                    )
                saveControllerForCleanup(original)
                original.setup()
                original.get().chooseAppFolder()
                advanceRobolectricLooper()
                assertTrue(workerPaused.await(10, TimeUnit.SECONDS))
                original.pause().stop()

                // This is the real external-entry route while storage is inaccessible.
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "dog")
                val entry = Robolectric.buildActivity(IntentHandler::class.java, share)
                saveControllerForCleanup(entry)
                // Robolectric does not route startActivityIfNeeded through ShadowInstrumentation.
                TaskRestartShadow.restart = null
                entry.create()
                val restart = assertNotNull(TaskRestartShadow.restart)
                assertEquals(DeckPicker::class.java.name, restart.component?.className)
                assertTrue(restart.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)

                // Apply the task-clearing lifecycle; unlike rotation it clears the ViewModelStore.
                original.get().finish()
                original.destroy()
                val deckPicker = Robolectric.buildActivity(DeckPicker::class.java, restart)
                saveControllerForCleanup(deckPicker)
                deckPicker.setup()
                advanceRobolectricLooper()
                val request = assertNotNull(shadowOf(deckPicker.get()).nextStartedActivityForResult).intent
                assertEquals(PermissionsActivity::class.java.name, request.component?.className)
                val replacement = Robolectric.buildActivity(PermissionsActivity::class.java, request)
                saveControllerForCleanup(replacement)
                replacement.setup()
                if (retryStartupWithoutPermissions) {
                    // Startup may retry after an earlier permission check; this is not a new storage choice.
                    InitialActivity.decideStorageIfUndecided(replacement.get())
                }
                if (restoringPublicAccess) {
                    withAllFilesAccess {
                        grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        val activity = replacement.get()
                        activity.binding.continueButton.performClick()
                        advanceRobolectricLooper()
                        assertTrue(activity.isFinishing)
                        assertEquals(publicCollectionPath, activity.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))

                        releaseWorker.countDown()
                        runBlocking { CollectionManager.ensureClosed() }
                        advanceRobolectricLooper()
                        assertEquals(
                            publicCollectionPath,
                            activity.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null),
                            "The destroyed task must not overwrite the replacement task's public storage choice",
                        )
                        verify(exactly = 0) { resetAnkiDroidDirectory(any(), any()) }
                    }
                    return@withManageExternalStorageInManifest
                }
                if (missingInternetWhileStopped) {
                    denyPermissions(Manifest.permission.INTERNET)
                    replacement.pause().stop().saveInstanceState(Bundle())
                }

                releaseWorker.countDown()
                runBlocking { CollectionManager.ensureClosed() }
                advanceRobolectricLooper()
                val activity = replacement.get()
                assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(activity))
                val selectedPath = activity.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)
                val button = activity.binding.continueButton
                if (missingInternetWhileStopped) {
                    assertFalse(activity.isFinishing)
                    replacement.start().resume()
                    advanceRobolectricLooper()
                    val fragment =
                        assertIs<InternetPermissionFragment>(activity.supportFragmentManager.findFragmentById(R.id.fragment_container))
                    val fragmentBinding = FragmentInternetPermissionBinding.bind(fragment.requireView())
                    assertTrue(fragmentBinding.internetPermission.isVisible)
                    assertFalse(button.isEnabled)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertFalse(activity.isFinishing)
                    replacement.pause().stop()
                    grantPermissions(Manifest.permission.INTERNET)
                    replacement.start().resume()
                    advanceRobolectricLooper()
                }
                assertTrue(selectStoragePermissions(activity).hasRequiredPermissions(activity))
                assertEquals(getResourceString(CommonString.dialog_continue), button.text)
                assertTrue(button.isEnabled)
                button.performClick()
                advanceRobolectricLooper()
                assertTrue(activity.isFinishing, "Continue must finish after the old task saves private storage")
                assertEquals(selectedPath, activity.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
                verify(exactly = 1) { resetAnkiDroidDirectory(any(), any()) }
            }
        } finally {
            releaseWorker.countDown()
            runBlocking { CollectionManager.ensureClosed() }
            CollectionManager.setTestDispatcher(previous)
            unmockkObject(CollectionHelper)
            unmockkStatic(::resetAnkiDroidDirectory)
            advanceRobolectricLooper()
        }
    }
}
