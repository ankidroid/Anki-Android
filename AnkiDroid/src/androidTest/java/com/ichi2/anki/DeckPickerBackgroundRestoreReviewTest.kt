// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerBackgroundRestoreReviewTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun backgroundRestorePersistsItsDestinationBeforeResuming() {
        disableIntroductionSlide()
        val preferences = testContext.sharedPrefs()
        InitialActivity.setUpgradedToLatestVersion(preferences)
        val hadBackupPrompt = preferences.contains("backupPromptDisabled")
        val oldBackupPrompt = preferences.getBoolean("backupPromptDisabled", false)
        preferences.edit { putBoolean("backupPromptDisabled", true) }
        addNoteUsingBasicNoteType()
        val backupDirectory =
            File.createTempFile("restore-review-", "", testContext.cacheDir).apply {
                delete()
                mkdirs()
            }
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val backup = backupDirectory.listFiles()!!.single { it.extension == "colpkg" }
        val destination =
            File.createTempFile("restore-destination-", "", testContext.cacheDir).apply {
                delete()
                mkdirs()
            }
        val previousOverride = CollectionHelper.ankiDroidDirectoryOverride
        val oldPath = preferences.getString(CollectionHelper.PREF_COLLECTION_PATH, null)
        try {
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                lateinit var original: DeckPicker
                scenario.onActivity { original = it }
                val queueHeld = CountDownLatch(1)
                val releaseQueue = CountDownLatch(1)
                val blocker =
                    CoroutineScope(Dispatchers.IO).launch {
                        CollectionManager.withCol {
                            queueHeld.countDown()
                            check(releaseQueue.await(30, TimeUnit.SECONDS))
                        }
                    }
                try {
                    assertTrue(queueHeld.await(10, TimeUnit.SECONDS), "The collection queue must be held")
                    scenario.onActivity { picker ->
                        CollectionHelper.ankiDroidDirectoryOverride = destination
                        picker.importColpkgListener = DatabaseRestorationListener(picker, destination)
                        picker.importReplace(backup.path)
                    }
                    scenario.moveToState(Lifecycle.State.CREATED)
                } finally {
                    releaseQueue.countDown()
                    runBlocking { blocker.join() }
                }
                waitUntil(message = { "The backup import did not complete while stopped" }) {
                    original.viewModel.replacedCollection.value == backup.path
                }
                assertEquals(
                    destination.absolutePath,
                    preferences.getString(CollectionHelper.PREF_COLLECTION_PATH, null),
                    "The completed restore must persist its destination even while the activity is stopped",
                )
            }
        } finally {
            CollectionHelper.ankiDroidDirectoryOverride = previousOverride
            preferences.edit {
                if (oldPath == null) {
                    remove(CollectionHelper.PREF_COLLECTION_PATH)
                } else {
                    putString(CollectionHelper.PREF_COLLECTION_PATH, oldPath)
                }
                if (hadBackupPrompt) putBoolean("backupPromptDisabled", oldBackupPrompt) else remove("backupPromptDisabled")
            }
        }
    }
}
