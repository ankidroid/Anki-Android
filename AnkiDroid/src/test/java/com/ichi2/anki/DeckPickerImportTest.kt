/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.ImportDialog
import com.ichi2.anki.testutils.SingleViewModelFactory
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.ImportUtils
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerImportTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var qualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic
        fun configurations(): Collection<String> = listOf("normal", "xlarge")
    }

    @Before
    fun configureScreen() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        setIntroductionSlidesShown(true)
        InitialActivity.setUpgradedToLatestVersion(getPreferences())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
    }

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `import confirmation survives delayed startup`() {
        val viewModel = spy(DeckPickerViewModel())
        lateinit var startupEnvironment: AnkiDroidEnvironment
        doAnswer { invocation ->
            startupEnvironment = invocation.getArgument(0)
            null
        }.whenever(viewModel).handleStartup(any())

        DialogHandler.storeMessage(ImportUtils.CollectionImportAdd("deck.apkg").toMessage())
        val controller = Robolectric.buildActivity(DeckPicker::class.java)
        saveControllerForCleanup(controller)
        val activity = controller.get()
        ViewModelProvider(activity, SingleViewModelFactory.create(viewModel))[DeckPickerViewModel::class.java]
        controller
            .create()
            .start()
            .resume()
            .visible()
        advanceRobolectricLooper()
        activity.assertImportConfirmationVisible()

        // Opening the collection at startup can finish after the pending import dialog is shown.
        doCallRealMethod().whenever(viewModel).handleStartup(any())
        viewModel.handleStartup(startupEnvironment)
        viewModel.awaitStartup()
        advanceRobolectricLooper()

        activity.assertImportConfirmationVisible()
    }

    @Test
    fun `import confirmation survives startup`() {
        DialogHandler.storeMessage(ImportUtils.CollectionImportAdd("deck.apkg").toMessage())

        deckPicker {
            assertImportConfirmationVisible()
        }
    }

    @Test
    fun `import confirmation survives returning from file picker`() {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        controller.pause().stop()
        DialogHandler.storeMessage(ImportUtils.CollectionImportAdd("deck.apkg").toMessage())

        controller.start().resume().visible()
        advanceRobolectricLooper()

        controller.get().assertImportConfirmationVisible()
    }

    private fun DeckPicker.assertImportConfirmationVisible() {
        val fragment = assertNotNull(supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? ImportDialog)
        assertTrue(assertNotNull(fragment.dialog as? AlertDialog).isShowing)
    }

    @Test
    fun importAddShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    @Test
    fun replaceShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    private class DeckPickerImport : DeckPicker() {
        private var dialogFragment: AsyncDialogFragment? = null

        fun getAsyncDialogFragmentClass(): Class<*> {
            if (dialogFragment == null) {
                fail("No async fragment shown")
            }
            return dialogFragment!!.javaClass
        }

        override fun showAsyncDialogFragment(newFragment: AsyncDialogFragment) {
            dialogFragment = newFragment
            super.showAsyncDialogFragment(newFragment)
        }
    }
}
