// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.Backend
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerQueuedStorageRestoreRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `a queued restore that replaces the collection persists its destination after recreation`() {
        val original = tempFolder.newFolder("original-collection")
        getPreferences().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, original.absolutePath) }
        val imported = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun importCollectionPackage(
                    colPath: String,
                    backupPath: String,
                    mediaFolder: String,
                    mediaDb: String,
                ) {
                    super.importCollectionPackage(colPath, backupPath, mediaFolder, mediaDb)
                    imported.set(true)
                }
            }
        }
        addBasicNote()
        val backupDirectory = BackupManager.getBackupDirectory(original)
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val backup = backupDirectory.listFiles()!!.single { it.extension == "colpkg" }
        val destination = tempFolder.newFolder("restored-collection")

        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        targetContext.sharedPrefs().edit {
            putBoolean("backupPromptDisabled", true)
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), false)
        }
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val picker = controller.get()
        picker.viewModel.awaitStartup()
        advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }

        val queueHeld = CountDownLatch(1)
        val releaseQueue = CountDownLatch(1)
        val previousOverride = CollectionHelper.ankiDroidDirectoryOverride
        val previousDispatcher =
            CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            val blocker =
                CoroutineScope(Dispatchers.IO).launch {
                    CollectionManager.withCol {
                        queueHeld.countDown()
                        check(releaseQueue.await(30, TimeUnit.SECONDS))
                    }
                }
            assertTrue(queueHeld.await(10, TimeUnit.SECONDS), "The collection queue was never blocked")
            CollectionHelper.ankiDroidDirectoryOverride = destination
            picker.importColpkgListener = DatabaseRestorationListener(picker, destination)
            picker.importReplace(backup.path)
            advanceRobolectricLooper()
            controller.recreate()
            val recreated = controller.get()
            assertTrue(recreated !== picker)
            assertTrue(!imported.get(), "The import must still be queued during recreation")
            releaseQueue.countDown()

            var queueDrained = false
            recreated.lifecycleScope.launch {
                CollectionManager.withCol { }
                queueDrained = true
            }
            advanceRobolectricLooperUntil { blocker.isCompleted && queueDrained }
            advanceRobolectricLooper()
            if (imported.get()) {
                assertEquals(
                    destination.absolutePath,
                    getPreferences().getString(CollectionHelper.PREF_COLLECTION_PATH, null),
                    "A completed restore must persist its destination before the next cold launch",
                )
            }
        } finally {
            releaseQueue.countDown()
            CollectionHelper.ankiDroidDirectoryOverride = previousOverride
            CollectionManager.setTestDispatcher(previousDispatcher)
        }
    }
}
