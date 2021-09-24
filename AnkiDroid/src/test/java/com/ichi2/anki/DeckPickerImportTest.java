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

package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.anki.dialogs.AsyncDialogFragment;
import com.ichi2.anki.dialogs.ImportDialog;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class DeckPickerImportTest extends RobolectricTest {

    @Test
    public void importAddShowsImportDialog() {
        DeckPickerImport deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport.class, new Intent());

        deckPicker.showImportDialog(ImportDialog.DIALOG_IMPORT_ADD_CONFIRM, "");

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog.class));
    }

    @Test
    public void replaceShowsImportDialog() {
        DeckPickerImport deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport.class, new Intent());

        deckPicker.showImportDialog(ImportDialog.DIALOG_IMPORT_REPLACE_CONFIRM, "");

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog.class));
    }

    private static class DeckPickerImport extends DeckPicker {

        private AsyncDialogFragment mDialogFragment;

        private Class<?> getAsyncDialogFragmentClass() {
            if (mDialogFragment == null) {
                fail("No async fragment shown");
            }
            return mDialogFragment.getClass();
        }

        @Override
        public void showAsyncDialogFragment(AsyncDialogFragment newFragment) {
            this.mDialogFragment = newFragment;
            super.showAsyncDialogFragment(newFragment);
        }
    }
}
