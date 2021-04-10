/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.os.Looper;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.FilteredAncestor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.inject.Injector;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import timber.log.Timber;
import static android.os.Looper.getMainLooper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CreateDeckDialogTest extends RobolectricTest {

    private DeckPicker mActivityScenario;

//
//    @Test
//    public void setupDeckPicker() {
//        mActivityScenario = new DeckPicker();
//    }

    private static <T> T whatever() {
        return null;
    }
    @Test
    public void testCreateFilteredDeckFunction() throws FilteredAncestor{
            ensureCollectionLoadIsSynchronous();
//            shadowOf(Looper.getMainLooper()).idle();
//        Bundle args = new CustomStudyDialog(whatever(), whatever())
//                .withArguments(CustomStudyDialog.CUSTOM_STUDY_AHEAD, 1)
//                .getArguments();

        ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class);

        scenario.moveToState(Lifecycle.State.STARTED);
            scenario.onActivity(activity -> {
                AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
                CreateDeckDialog createDeckDialog =new CreateDeckDialog(activity, R.string.new_deck, false, null);
                String deckName = "filteredDeck";
                shadowOf(getMainLooper()).idle();
                createDeckDialog.createFilteredDeck(deckName);
                createDeckDialog.setOnNewDeckCreated((id) -> {
                    // a deck was created
                    isCreated.set(true);
                    try {
                        assertThat(id, is(new AnkiActivity().getCol().getDecks().id(deckName)));
                    } catch (FilteredAncestor filteredAncestor) {
                        filteredAncestor.printStackTrace();
                    }
                });
                assertThat(isCreated.get(), is(false));
            });
    }
//
//    @Test
//    public void testCreateSubDeckFunction() {
//        try {
//            shadowOf(getMainLooper()).idle();
//            Long mParentId = mActivityScenario.getCol().getDecks().id("filteredDeck");
//            String deckName = "I am child";
//            mActivityScenario.mCreateDeckDialog.createSubDeck(mParentId, deckName);
//            mActivityScenario.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
//                try {
//                    String deckNameWithParentName = mActivityScenario.getCol().getDecks().getSubdeckName(mParentId, deckName);
//                    assertThat(id, is(mActivityScenario.getCol().getDecks().id(deckNameWithParentName)));
//                } catch (FilteredAncestor filteredAncestor) {
//                    filteredAncestor.printStackTrace();
//                }
//            });
//        } catch (Exception e) {
//            Timber.w(e);
//        }
//    }
//
//    @Test
//    public void testCreateDeckFunction() {
//        try {
//            shadowOf(getMainLooper()).idle();
//            String deckName = "Sample Deck Name";
//            mActivityScenario.mCreateDeckDialog.createDeck(deckName);
//            mActivityScenario.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
//                // a deck was created
//                try {
//                    assertThat(id, is(mActivityScenario.getCol().getDecks().id(deckName)));
//                } catch (FilteredAncestor filteredAncestor) {
//                    filteredAncestor.printStackTrace();
//                }
//            });
//        } catch (Exception e) {
//            Timber.w(e);
//        }
//    }
}