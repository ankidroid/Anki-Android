/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.anki.dialogs.DatabaseErrorDialog;
import com.ichi2.async.CollectionTask.TaskData;
import com.ichi2.libanki.Collection.CheckDatabaseResult;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
public class DeckPickerCheckDatabaseListenerTest extends RobolectricTest {

    private DeckPickerTestImpl impl;

    @Override
    public void setUp() {
        super.setUp();
        //.visible() crashes: Layout state should be one of 100 but it is 10
        ActivityController<DeckPickerTestImpl> controller =
                Robolectric.buildActivity(DeckPickerTestImpl.class, new Intent())
                .create().start().resume();
        impl = controller.get();
        impl.resetVariables();
    }

    @Test
    public void failedResultWithNoDataWillDisplayFailedDialog() {
        TaskData result = failedResultNoData();

        execute(result);

        assertThat("Load Failed dialog should be shown if no data is supplied", impl.didDisplayDialogLoadFailed());
    }

    @Test
    public void failedResultWithEmptyDataWillDisplayFailedDialog() {
        TaskData result = failedResultWithData();

        execute(result);

        assertThat("Load Failed dialog should be shown if empty data is supplied", impl.didDisplayDialogLoadFailed());
    }

    @Test
    public void validResultWithEmptyDataWillDisplayFailedDialog() {
        TaskData result = validResultWithData();

        execute(result);

        assertThat("Load Failed dialog should be shown if empty data is supplied", impl.didDisplayDialogLoadFailed());
    }

    @Test
    public void failedResultWithInvalidDataWillDisplayFailedDialog() {
        TaskData result = failedResultWithData(1);

        execute(result);

        assertThat("Load Failed dialog should be shown if invalid data is supplied", impl.didDisplayDialogLoadFailed());
    }

    @Test
    @Ignore("Currently failing")
    public void validResultWithInvalidDataWillDisplayFailedDialog() {
        TaskData result = validResultWithData(1);

        execute(result);

        assertThat("Load Failed dialog should be shown if invalid data is supplied", impl.didDisplayDialogLoadFailed());
    }

    @Test
    public void validResultWithValidDataWillDisplayMessageBox() {
        CheckDatabaseResult validData = validData();
        TaskData result = validResultWithData(validData);

        execute(result);

        assertThat("Load Failed dialog should not be shown if invalid data is supplied", !impl.didDisplayDialogLoadFailed());
        assertThat("Dialog should be displayed", impl.didDisplayMessage());
    }


    @NonNull
    private CheckDatabaseResult validData() {
        return new CheckDatabaseResult(1);
    }


    @NonNull
    private TaskData failedResultWithData(Object... obj) {
        return new TaskData(false, obj);
    }

    @NonNull
    private TaskData validResultWithData(Object... obj) {
        return new TaskData(true, obj);
    }


    @NonNull
    private TaskData failedResultNoData() {
        return new TaskData(false);
    }

    private void execute(TaskData result) {
        DeckPicker.CheckDatabaseListener listener = getInstance(impl);

        listener.onPostExecute(result);
    }

    @NonNull
    private DeckPicker.CheckDatabaseListener getInstance(DeckPickerTestImpl test) {
        return test.new CheckDatabaseListener();
    }

    /**COULD_BE_BETTER: Listener is too coupled to this */
    protected static class DeckPickerTestImpl extends DeckPicker {

        private boolean mDidDisplayDialogLoadFailed;
        private boolean mDidDisplayMessage = false;


        public boolean didDisplayDialogLoadFailed() {
            return mDidDisplayDialogLoadFailed;
        }

        @Override
        public void handleDbError() {
            this.mDidDisplayDialogLoadFailed = true;
            super.handleDbError();
            showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED);
        }

        public void resetVariables() {
            mDidDisplayMessage = false;
            mDidDisplayDialogLoadFailed = false;
        }

        @Override
        protected void showSimpleMessageDialog(String message, boolean reload) {
            mDidDisplayMessage = true;
            super.showSimpleMessageDialog(message, reload);
        }


        public boolean didDisplayMessage() {
            return mDidDisplayMessage;
        }
    }
}
