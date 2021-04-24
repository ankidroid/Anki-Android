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

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.FilteredAncestor;
import com.ichi2.libanki.Decks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicReference;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
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
        ensureCollectionLoadIsSynchronous();
        mActivityScenario = ActivityScenario.launch(DeckPicker.class);
        mActivityScenario.moveToState(Lifecycle.State.STARTED);
    }

    @Test
    public void testCreateFilteredDeckFunction() {
        mActivityScenario.onActivity(activity -> {
            CreateDeckDialog createDeckDialog = new CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null);
            AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
            String deckName = "filteredDeck";
            advanceRobolectricLooper();

            createDeckDialog.setOnNewDeckCreated((id) -> {
                // a deck was created
                try {
                    isCreated.set(true);
                    final Decks decks = activity.getCol().getDecks();
                    assertThat(id, is(decks.id(deckName)));
                } catch (FilteredAncestor filteredAncestor) {
                    throw new RuntimeException(filteredAncestor);
                }
            });

            createDeckDialog.createFilteredDeck(deckName);
            assertThat(isCreated.get(), is(true));
        });
    }

    @Test
    public void testCreateSubDeckFunction() throws FilteredAncestor {
        Long deckParentId = new AnkiActivity().getCol().getDecks().id("Deck Name");

        mActivityScenario.onActivity(activity -> {
            CreateDeckDialog createDeckDialog = new CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.SUB_DECK, deckParentId);
            AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
            String deckName = "filteredDeck";
            advanceRobolectricLooper();

            createDeckDialog.setOnNewDeckCreated((id) -> {
                try {
                    isCreated.set(true);
                    final Decks decks = activity.getCol().getDecks();
                    String deckNameWithParentName = decks.getSubdeckName(deckParentId, deckName);
                    assertThat(id, is(decks.id(deckNameWithParentName)));
                } catch (FilteredAncestor filteredAncestor) {
                    throw new RuntimeException(filteredAncestor);
                }
            });

            createDeckDialog.createSubDeck(deckParentId, deckName);
            assertThat(isCreated.get(), is(true));
        });
    }

    @Test
    public void testCreateDeckFunction() {
        mActivityScenario.onActivity(activity -> {
            CreateDeckDialog createDeckDialog = new CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null);
            AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
            String deckName = "Deck Name";
            advanceRobolectricLooper();

            createDeckDialog.setOnNewDeckCreated((id) -> {
                // a deck was created
                isCreated.set(true);
                final Decks decks = activity.getCol().getDecks();
                assertThat(id, is(decks.byName(deckName).getLong("id")));
            });

            createDeckDialog.createDeck(deckName);
            assertThat(isCreated.get(), is(true));
        });
    }

    @Test
    public void testRenameDeckFunction() {
        String deckName = "Deck Name";
        String deckNewName = "New Deck Name";
        mActivityScenario.onActivity(activity -> {

            CreateDeckDialog createDeckDialog = new CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null);
            createDeckDialog.setDeckName(deckName);
            AtomicReference<Boolean> isCreated = new AtomicReference<>(false);
            advanceRobolectricLooper();

            createDeckDialog.setOnNewDeckCreated((id) -> {
                // a deck name was renamed
                isCreated.set(true);
                final Decks decks = activity.getCol().getDecks();
                assertThat(deckNewName, is(decks.name(id)));
            });

            createDeckDialog.renameDeck(deckNewName);
            assertThat(isCreated.get(), is(true));
        });
    }

} 