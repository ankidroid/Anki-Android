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
import android.util.Pair;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Collection.CheckDatabaseResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
public class DeckPickerCheckDatabaseListenerTest extends RobolectricTest {

    private DeckPickerTestImpl mImpl;

    @Override
    public void setUp() {
        super.setUp();
        //.visible() crashes: Layout state should be one of 100 but it is 10
        ActivityController<DeckPickerTestImpl> controller =
                Robolectric.buildActivity(DeckPickerTestImpl.class, new Intent())
                .create().start().resume();
        saveControllerForCleanup((controller));
        mImpl = controller.get();
        mImpl.resetVariables();
    }

    @Test
    public void failedResultWithNoDataWillDisplayFailedDialog() {
        Pair<Boolean, Collection.CheckDatabaseResult> result = failedResultNoData();

        execute(result);

        assertThat("Load Failed dialog should be shown if no data is supplied", mImpl.didDisplayDialogLoadFailed());
    }

    @Test
    public void failedResultWithEmptyDataWillDisplayFailedDialog() {
        CheckDatabaseResult validData = validData();
        Pair<Boolean, Collection.CheckDatabaseResult> result = failedResultWithData(validData);

        execute(result);

        assertThat("Load Failed dialog should be shown if empty data is supplied", mImpl.didDisplayDialogLoadFailed());
    }

    @Test
    public void validResultWithValidDataWillDisplayMessageBox() {
        CheckDatabaseResult validData = validData();
        Pair<Boolean, Collection.CheckDatabaseResult> result = validResultWithData(validData);

        execute(result);

        assertThat("Load Failed dialog should not be shown if invalid data is supplied", !mImpl.didDisplayDialogLoadFailed());
        assertThat("Dialog should be displayed", mImpl.didDisplayMessage());
    }

    @Test
    public void validResultWithFailedDatabaseWillShowFailedDialog() {
        CheckDatabaseResult failedDb = failedDatabase();
        Pair<Boolean, Collection.CheckDatabaseResult> result = validResultWithData(failedDb);

        execute(result);

        assertThat("Load Failed dialog should be shown if failed data is supplied", mImpl.didDisplayDialogLoadFailed());
        assertThat("Locked Database dialog should be shown if Db was locked", !mImpl.didDisplayLockedDialog());
        assertThat("Dialog should not be displayed", !mImpl.didDisplayMessage());
    }

    @Test
    public void validResultWithLockedDatabaseWillShowLockedDialog() {
        CheckDatabaseResult lockedDb = lockedDatabase();
        Pair<Boolean, Collection.CheckDatabaseResult> result = validResultWithData(lockedDb);

        execute(result);

        assertThat("Load Failed dialog should not be shown if invalid data is supplied", !mImpl.didDisplayDialogLoadFailed());
        assertThat("Locked Database dialog should be shown if Db was locked", mImpl.didDisplayLockedDialog());
        assertThat("Dialog should not be displayed", !mImpl.didDisplayMessage());
    }

    @NonNull
    private CheckDatabaseResult lockedDatabase() {
        return new CheckDatabaseResult(1).markAsLocked();
    }

    @NonNull
    private CheckDatabaseResult failedDatabase() {
        return new CheckDatabaseResult(1).markAsFailed();
    }


    @NonNull
    private CheckDatabaseResult validData() {
        return new CheckDatabaseResult(1);
    }


    @NonNull
    private Pair<Boolean, Collection.CheckDatabaseResult> failedResultWithData(Collection.CheckDatabaseResult obj) {
        return new Pair<>(false, obj);
    }

    @NonNull
    private Pair<Boolean, Collection.CheckDatabaseResult> validResultWithData(Collection.CheckDatabaseResult obj) {
        return new Pair<>(true, obj);
    }


    @NonNull
    private Pair<Boolean, Collection.CheckDatabaseResult> failedResultNoData() {
        return new Pair<>(false, null);
    }

    private void execute(Pair<Boolean, CheckDatabaseResult> result) {
        DeckPicker.CheckDatabaseListener listener = getInstance(mImpl);

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
        private boolean mDidDisplayDbLocked = false;


        public boolean didDisplayDialogLoadFailed() {
            return mDidDisplayDialogLoadFailed;
        }

        @Override
        public void handleDbError() {
            this.mDidDisplayDialogLoadFailed = true;
            super.handleDbError();
        }

        @Override
        public void handleDbLocked() {
            this.mDidDisplayDbLocked = true;
            super.handleDbLocked();
        }

        public void resetVariables() {
            mDidDisplayMessage = false;
            mDidDisplayDialogLoadFailed = false;
            mDidDisplayDbLocked = false;
        }

        @Override
        public void showSimpleMessageDialog(String message, boolean reload) {
            mDidDisplayMessage = true;
            super.showSimpleMessageDialog(message, reload);
        }


        public boolean didDisplayMessage() {
            return mDidDisplayMessage;
        }


        public boolean didDisplayLockedDialog() {
            return mDidDisplayDbLocked;
        }
    }
}
