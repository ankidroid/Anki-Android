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

package com.ichi2.anki.dialogs;

import android.os.Bundle;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener;
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.sched.AbstractSched;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Mockito.*;

@RunWith(AndroidJUnit4.class)
public class CustomStudyDialogTest extends RobolectricTest {

    CustomStudyListener mockListener;


    @Override
    public void setUp() {
        super.setUp();
        mockListener = mock(CustomStudyListener.class);
    }


    @Override
    @After
    public void tearDown() {
        super.tearDown();
        reset(mockListener);
    }


    private static <T> T whatever() {
        return null;
    }

    @Test
    public void learnAheadCardsRegressionTest() {
        // #6289 - Regression Test
        Bundle args = new CustomStudyDialog(whatever(), whatever())
                .withArguments(CustomStudyDialog.CUSTOM_STUDY_AHEAD, 1)
                .getArguments();


        CustomStudyDialogFactory factory = new CustomStudyDialogFactory(this::getCol, mockListener);
        FragmentScenario<CustomStudyDialog> scenario = FragmentScenario.launch(CustomStudyDialog.class, args, factory);

        scenario.moveToState(Lifecycle.State.STARTED);

        scenario.onFragment(f -> {
            MaterialDialog dialog = (MaterialDialog) f.getDialog();
            assertThat(dialog, notNullValue());
            dialog.getActionButton(DialogAction.POSITIVE).callOnClick();
        });


        Deck customStudy = getCol().getDecks().current();
        assertThat("Custom Study should be dynamic", customStudy.isDyn());
        assertThat("could not find deck: Custom study session", customStudy, notNullValue());
        customStudy.remove("id");
        customStudy.remove("mod");
        customStudy.remove("name");

        String expected = "{\"newToday\":[0,0],\"revToday\":[0,0],\"lrnToday\":[0,0],\"timeToday\":[0,0],\"collapsed\":false,\"dyn\":1,\"desc\":\"\",\"usn\":-1,\"delays\":null,\"separate\":true,\"terms\":[[\"deck:\\\"Default\\\" prop:due<=1\",99999,6]],\"resched\":true,\"return\":true}";
        assertThat(customStudy.toString(), is(expected));
    }


    @Test
    @Config(qualifiers = "en")
    public void increaseNewCardLimitRegressionTest(){
        // #8338 - Regression Test
        Bundle args = new CustomStudyDialog(whatever(), whatever())
                .withArguments(CustomStudyDialog.CONTEXT_MENU_STANDARD, 1)
                .getArguments();

        // we are using mock collection for the CustomStudyDialog but still other parts of the code
        // access a real collection, so we must ensure that collection is loaded first
        // so we don't get net/ankiweb/rsdroid/BackendException$BackendDbException$BackendDbLockedException
        ensureCollectionLoadIsSynchronous();

        Collection mockCollection = mock(Collection.class, Mockito.RETURNS_DEEP_STUBS);
        AbstractSched mockSched = mock(AbstractSched.class);
        when(mockCollection.getSched()).thenReturn(mockSched);
        when(mockSched.newCount()).thenReturn(0);


        CustomStudyDialogFactory factory = new CustomStudyDialogFactory(() -> mockCollection, mockListener);
        FragmentScenario<CustomStudyDialog> scenario = FragmentScenario.launch(CustomStudyDialog.class, args, R.style.Theme_AppCompat, factory);

        scenario.moveToState(Lifecycle.State.STARTED);

        scenario.onFragment(f -> {
            MaterialDialog dialog = (MaterialDialog)f.getDialog();
            assertThat(dialog,notNullValue());
            assertThat(dialog.getItems(), Matchers.not(Matchers.hasItem(getResourceString(R.string.custom_study_increase_new_limit))));
        });
    }
}
