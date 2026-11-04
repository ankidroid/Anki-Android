// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.databinding.FragmentInternetPermissionBinding
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.anki.startup.resetAnkiDroidDirectory
import com.ichi2.testutils.denyPermissions
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.withManageExternalStorageInManifest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R])
class PermissionsActivityLifecycleTest : RobolectricTest() {
    @Test
    fun `rotation during fresh app folder setup must not require storage permission`() = checkRotation()

    @Test
    fun `app folder choice stays visible through completion on the real queue`() = checkRotation(rotate = false)

    @Test
    fun `fresh app folder setup finishes if rotation happens before the path is written`() =
        checkRotation(pauseAt = StorageChangePause.BEFORE_WRITE)

    @Test
    fun `fresh app folder setup failure after rotation reports the error and allows retry`() =
        checkRotation(pauseAt = StorageChangePause.FAIL_BEFORE_WRITE)

    @Test
    fun `completion while stopped waits for resume and requires Internet`() =
        checkRotation(rotate = false, pauseAt = StorageChangePause.AFTER_WRITE_WHILE_STOPPED)

    private enum class StorageChangePause {
        BEFORE_WRITE,
        AFTER_WRITE,
        AFTER_WRITE_WHILE_STOPPED,
        FAIL_BEFORE_WRITE,
    }

    private fun checkRotation(
        rotate: Boolean = true,
        pauseAt: StorageChangePause = StorageChangePause.AFTER_WRITE,
    ) {
        targetContext.sharedPrefs().edit {
            remove(CollectionHelper.PREF_COLLECTION_PATH)
        }
        val workerPaused = CountDownLatch(1)
        val allowWorkerToFinish = CountDownLatch(1)
        val writeBeforePause = pauseAt == StorageChangePause.AFTER_WRITE || pauseAt == StorageChangePause.AFTER_WRITE_WHILE_STOPPED
        var failStorageChange = pauseAt == StorageChangePause.FAIL_BEFORE_WRITE
        val previous = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        mockkStatic(::resetAnkiDroidDirectory)
        every { resetAnkiDroidDirectory(any(), any()) } answers {
            if (writeBeforePause) callOriginal()
            workerPaused.countDown()
            check(allowWorkerToFinish.await(20, TimeUnit.SECONDS))
            if (failStorageChange) {
                failStorageChange = false
                throw StorageAccessException("Test storage failure")
            }
            if (!writeBeforePause) callOriginal()
        }
        try {
            withManageExternalStorageInManifest {
                val intent = PermissionsActivity.getIntent(targetContext, selectStoragePermissions(targetContext))
                val controller = Robolectric.buildActivity(PermissionsActivity::class.java, intent)
                saveControllerForCleanup(controller)
                controller.setup()
                val originalFragment = assertNotNull(controller.get().supportFragmentManager.findFragmentById(R.id.fragment_container))

                fun assertChoiceUnchanged() {
                    val activity = controller.get()
                    assertEquals(getResourceString(CommonString.allow_file_access), activity.binding.continueButton.text)
                    assertEquals(getResourceString(CommonString.storage_setup_title), activity.binding.headline.text)
                    assertTrue(activity.binding.storagePermissionExplanation.isVisible)
                    assertSame(originalFragment, activity.supportFragmentManager.findFragmentById(R.id.fragment_container))
                }

                controller.get().chooseAppFolder()
                advanceRobolectricLooper()
                assertTrue(workerPaused.await(10, TimeUnit.SECONDS), "The storage worker must reach the preference write")
                if (writeBeforePause) {
                    assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(targetContext))
                }
                if (!rotate && pauseAt == StorageChangePause.AFTER_WRITE) {
                    // Deliver the preference notification while the worker has not returned yet.
                    advanceRobolectricLooper()
                    assertChoiceUnchanged()
                }

                // Rotate while the worker is still running, on either side of the preference write.
                if (rotate) controller.recreate()
                if (pauseAt == StorageChangePause.AFTER_WRITE_WHILE_STOPPED) {
                    denyPermissions(Manifest.permission.INTERNET)
                    controller.pause().stop().saveInstanceState(Bundle())
                }
                val binding = controller.get().binding
                assertFalse(binding.continueWithoutAccess.isEnabled)
                assertTrue(binding.continueButton.isEnabled)
                assertEquals(getResourceString(CommonString.allow_file_access), binding.continueButton.text)
                assertTrue(binding.storagePermissionExplanation.isVisible)
                allowWorkerToFinish.countDown()
                runBlocking { CollectionManager.ensureClosed() }
                advanceRobolectricLooper()
                val recreated = controller.get()
                if (pauseAt == StorageChangePause.FAIL_BEFORE_WRITE) {
                    advanceRobolectricLooperUntil {
                        ShadowToast.getTextOfLatestToast() == getResourceString(CommonString.something_wrong)
                    }
                    assertFalse(recreated.isFinishing)
                    assertNull(targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null))
                    assertTrue(recreated.binding.continueWithoutAccess.isEnabled)
                    assertEquals(getResourceString(CommonString.allow_file_access), recreated.binding.continueButton.text)
                    recreated.chooseAppFolder()
                    advanceRobolectricLooper()
                }
                if (pauseAt == StorageChangePause.AFTER_WRITE_WHILE_STOPPED) {
                    assertFalse(recreated.isFinishing)
                    controller.start().resume()
                    advanceRobolectricLooper()
                    val fragment =
                        assertIs<InternetPermissionFragment>(recreated.supportFragmentManager.findFragmentById(R.id.fragment_container))
                    val fragmentBinding = FragmentInternetPermissionBinding.bind(fragment.requireView())
                    assertTrue(fragmentBinding.internetPermission.isVisible)
                    assertFalse(recreated.binding.continueButton.isEnabled)
                    controller.pause().stop()
                    grantPermissions(Manifest.permission.INTERNET)
                    controller.start().resume()
                    advanceRobolectricLooper()
                    assertTrue(recreated.binding.continueButton.isEnabled)
                    recreated.binding.continueButton.performClick()
                }
                advanceRobolectricLooperUntil { recreated.isFinishing }
                if (!rotate && pauseAt == StorageChangePause.AFTER_WRITE) {
                    assertChoiceUnchanged()
                }
                assertEquals(StoragePermissionSet.APP_PRIVATE, selectStoragePermissions(recreated))
                assertTrue(selectStoragePermissions(recreated).hasRequiredPermissions(recreated))
                verify(exactly = if (pauseAt == StorageChangePause.FAIL_BEFORE_WRITE) 2 else 1) {
                    resetAnkiDroidDirectory(any(), any())
                }
            }
        } finally {
            allowWorkerToFinish.countDown()
            runBlocking { CollectionManager.ensureClosed() }
            CollectionManager.setTestDispatcher(previous)
            unmockkStatic(::resetAnkiDroidDirectory)
            advanceRobolectricLooper()
        }
    }
}
