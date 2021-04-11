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
public class CreateDeckDialogTest extends RobolectricTest {

    private ActivityScenario<DeckPicker> mActivityScenario;

    @Override
    public void setUp() {
        super.setUp();
        mActivityScenario = ActivityScenario.launch(DeckPicker.class);
        mActivityScenario.moveToState(Lifecycle.State.STARTED);
    }

    @Test
    public void testCreateFilteredDeckFunction() throws FilteredAncestor{
        ensureCollectionLoadIsSynchronous();

        mActivityScenario.onActivity(activity -> {
            AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
            String deckName = "filteredDeck";
            shadowOf(getMainLooper()).idle();

            activity.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
                // a deck was created
                try {
                    isCreated.set(true);
                    assertThat(id, is(activity.getCol().getDecks().id(deckName)));
                } catch (FilteredAncestor filteredAncestor) {
                    filteredAncestor.printStackTrace();
                }
            });

            activity.mCreateDeckDialog.createFilteredDeck(deckName);
            assertThat(isCreated.get(), is(true));
        });
    }

    @Test
    public void testCreateSubDeckFunction() throws FilteredAncestor{
        ensureCollectionLoadIsSynchronous();
        Long deckParentId = new AnkiActivity().getCol().getDecks().id("Deck Name");

        mActivityScenario.onActivity(activity -> {
            AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
            String deckName = "filteredDeck";
            shadowOf(getMainLooper()).idle();

            activity.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
                try {
                    isCreated.set(true);
                    String deckNameWithParentName = activity.getCol().getDecks().getSubdeckName(deckParentId, deckName);
                    assertThat(id, is(activity.getCol().getDecks().id(deckNameWithParentName)));
                } catch (FilteredAncestor filteredAncestor) {
                    filteredAncestor.printStackTrace();
                }
            });

            activity.mCreateDeckDialog.createSubDeck(deckParentId, deckName);
            assertThat(isCreated.get(), is(true));
        });
    }

    @Test
    public void testCreateDeckFunction() throws FilteredAncestor{
        ensureCollectionLoadIsSynchronous();

        mActivityScenario.onActivity(activity -> {
            AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
            String deckName = "Deck Name";
            shadowOf(getMainLooper()).idle();

            activity.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
                // a deck was created
                try {
                    isCreated.set(true);
                    assertThat(id, is(activity.getCol().getDecks().id(deckName)));
                } catch (FilteredAncestor filteredAncestor) {
                    filteredAncestor.printStackTrace();
                }
            });
            
            activity.mCreateDeckDialog.createDeck(deckName);
            assertThat(isCreated.get(), is(true));
        });
    }
}