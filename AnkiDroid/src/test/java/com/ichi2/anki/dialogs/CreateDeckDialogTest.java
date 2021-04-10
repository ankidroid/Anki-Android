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

import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.exception.FilteredAncestor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import timber.log.Timber;
import static android.os.Looper.getMainLooper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class CreateDeckDialogTest {

    private DeckPicker mActivityScenario;

    @Test
    public void setupDeckPicker() {
        mActivityScenario = new DeckPicker();
    }

    @Test
    public void testCreateFilteredDeckFunction() {
        try {
            shadowOf(getMainLooper()).idle();
            String deckName = "filteredDeck";
            mActivityScenario.mCreateDeckDialog.createFilteredDeck(deckName);
            mActivityScenario.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
                // a deck was created
                try {
                    assertThat(id, is(mActivityScenario.getCol().getDecks().id(deckName)));
                } catch (FilteredAncestor filteredAncestor) {
                    filteredAncestor.printStackTrace();
                }
            });
        } catch (Exception e) {
            Timber.w(e);
        }
    }

    @Test
    public void testCreateSubDeckFunction() {
        try {
            shadowOf(getMainLooper()).idle();
            Long mParentId = mActivityScenario.getCol().getDecks().id("filteredDeck");
            String deckName = "I am child";
            mActivityScenario.mCreateDeckDialog.createSubDeck(mParentId, deckName);
            mActivityScenario.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
                try {
                    String deckNameWithParentName = mActivityScenario.getCol().getDecks().getSubdeckName(mParentId, deckName);
                    assertThat(id, is(mActivityScenario.getCol().getDecks().id(deckNameWithParentName)));
                } catch (FilteredAncestor filteredAncestor) {
                    filteredAncestor.printStackTrace();
                }
            });
        } catch (Exception e) {
            Timber.w(e);
        }
    }

    @Test
    public void testCreateDeckFunction() {
        try {
            shadowOf(getMainLooper()).idle();
            String deckName = "Sample Deck Name";
            mActivityScenario.mCreateDeckDialog.createDeck(deckName);
            mActivityScenario.mCreateDeckDialog.setOnNewDeckCreated((id) -> {
                // a deck was created
                try {
                    assertThat(id, is(mActivityScenario.getCol().getDecks().id(deckName)));
                } catch (FilteredAncestor filteredAncestor) {
                    filteredAncestor.printStackTrace();
                }
            });
        } catch (Exception e) {
            Timber.w(e);
        }
    }
}