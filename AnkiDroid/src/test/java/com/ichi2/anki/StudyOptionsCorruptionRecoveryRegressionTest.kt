// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CrashReportData.HelpAction
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class StudyOptionsCorruptionRecoveryRegressionTest : RobolectricTest() {
    @Before
    fun allowTheDeckPickerToOpen() {
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @Test
    fun `Options on a load failure opens the recovery options in the Deck Picker`() {
        val studyOptions = startRegularActivity<StudyOptionsActivity>()
        studyOptions.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)

        studyOptions.awaitDialog<DatabaseErrorDialog>().performPositiveClick()

        val recoveryOptions = studyOptions.openedDeckPicker().awaitDialog<DatabaseErrorDialog>()
        assertEquals(getResourceString(CommonString.error_handling_title), recoveryOptions.title)
    }

    @Test
    fun `Check Database from an error runs in the Deck Picker`() {
        val studyOptions = startRegularActivity<StudyOptionsActivity>()
        runBlocking { HelpAction.OpenCheckDatabase.execute(studyOptions) }

        studyOptions.openedDeckPicker().awaitDialog<DatabaseErrorDialog>().performPositiveClick()

        assertEquals(TR.databaseCheckRebuilt(), (ShadowDialog.getLatestDialog() as AlertDialog).message)
    }

    private fun AnkiActivity.openedDeckPicker(): DeckPicker {
        val intent = assertNotNull(shadowOf(this).nextStartedActivity, "the Deck Picker was not opened")
        assertEquals(DeckPicker::class.java.name, intent.component?.className)
        return startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, intent)
    }

    private inline fun <reified T : DialogFragment> AnkiActivity.awaitDialog(): AlertDialog {
        advanceRobolectricLooperUntil { supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) is T }
        return (supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as T).requireDialog() as AlertDialog
    }
}
