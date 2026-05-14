// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.databinding.FragmentPermissionsStartingAt30Binding
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R])
class PermissionsActivityConcurrentChoiceTest : RobolectricTest() {
    @Test
    @SuppressLint("InlinedApi") // The class's @Config guarantees API 30 for the Settings action.
    fun `granting storage permission supersedes app folder setup`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        val settingsScreen = ComponentName("com.android.settings", "AllFilesAccessSettings")
        shadowOf(targetContext.packageManager).apply {
            addActivityIfNotPresent(settingsScreen).exported = true
            addIntentFilterForActivity(
                settingsScreen,
                IntentFilter(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("package")
                },
            )
        }
        val workerPaused = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val previous = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        mockkObject(CollectionHelper)
        every { CollectionHelper.initializeAnkiDroidDirectory(any()) } answers {
            callOriginal()
            // Keep the original choice pending while the user grants public storage access.
            workerPaused.countDown()
            check(releaseWorker.await(30, TimeUnit.SECONDS))
        }
        try {
            withManageExternalStorageInManifest {
                val controller =
                    Robolectric.buildActivity(
                        PermissionsActivity::class.java,
                        PermissionsActivity.getIntent(targetContext, selectStoragePermissions(targetContext)),
                    )
                saveControllerForCleanup(controller)
                controller.setup()
                val activity = controller.get()
                activity.binding.continueButton.performClick()
                advanceRobolectricLooper()
                assertTrue(workerPaused.await(10, TimeUnit.SECONDS))

                val fragment = activity.binding.fragmentContainer.getFragment<PermissionsStartingAt30Fragment>()
                FragmentPermissionsStartingAt30Binding.bind(fragment.requireView()).allFilesPermission.performClick()
                val settingsIntent = assertNotNull(shadowOf(activity).nextStartedActivityForResult).intent
                assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, settingsIntent.action)
                controller.pause().stop()
                withAllFilesAccess {
                    controller.start().resume()
                    shadowOf(activity).receiveResult(settingsIntent, Activity.RESULT_CANCELED, Intent())
                    advanceRobolectricLooper()
                    assertEquals(publicCollectionPath, collectionPath, "The later public choice must be accepted immediately")
                    assertTrue(activity.isFinishing)

                    releaseWorker.countDown()
                    runBlocking { CollectionManager.ensureClosed() }
                    advanceRobolectricLooper()
                    assertEquals(publicCollectionPath, collectionPath, "The older app-folder worker must not overwrite the public choice")
                }
            }
        } finally {
            releaseWorker.countDown()
            runBlocking { CollectionManager.ensureClosed() }
            CollectionManager.setTestDispatcher(previous)
            unmockkObject(CollectionHelper)
            advanceRobolectricLooper()
        }
    }

    private val collectionPath: String?
        get() = targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)
}
