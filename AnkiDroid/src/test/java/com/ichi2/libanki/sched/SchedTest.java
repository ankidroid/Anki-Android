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

package com.ichi2.libanki.sched;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Note;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@RunWith(AndroidJUnit4.class)
public class SchedTest extends RobolectricTest {

    @Test
    public void unburyWorksIfDeckIsNotSelected() {
        //Issue 6200

        Sched sched = new Sched(getCol());
        Card buriedCard = createBuriedCardInDefaultDeck();
        assertThat(buriedCard.getDid(), is(Consts.DEFAULT_DECK_ID));

        assertThat("Card should be buried", getCardInDefaultDeck(sched), nullValue());

        //We want to assert that we can unbury, even if the deck we're unburying from isn't selected
        selectNewDeck();
        sched.unburyCardsForDeck(Consts.DEFAULT_DECK_ID);

        assertThat("Card should no longer be buried", getCardInDefaultDeck(sched), notNullValue());
    }


    private Card getCardInDefaultDeck(Sched s) {
        selectDefaultDeck();
        s.reset();
        return s.getCard();
    }


    @NonNull
    private Card createBuriedCardInDefaultDeck() {
        Note n = addNoteUsingBasicModel("Hello", "World");
        Card c = n.cards().get(0);
        c.setQueue(Consts.QUEUE_TYPE_SIBLING_BURIED);
        c.flush();
        return c;
    }


    private void selectNewDeck() {
        long did = addDeck("New");
        getCol().getDecks().select(did);
    }

    private void selectDefaultDeck() {
        getCol().getDecks().select(Consts.DEFAULT_DECK_ID);
    }
}