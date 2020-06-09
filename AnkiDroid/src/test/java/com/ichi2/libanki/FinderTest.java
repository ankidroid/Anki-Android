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

package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.sched.SchedV2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;

@RunWith(AndroidJUnit4.class)
public class FinderTest extends RobolectricTest {

    @Test
    @Config(qualifiers = "en")
    public void searchForBuriedReturnsManuallyAndSiblingBuried() {
        final String searchQuery = "is:buried";

        SchedV2 sched = upgradeToSchedV2();  //needs to be first

        enableBurySiblings();
        super.addNoteUsingModelName("Basic (and reversed card)", "Front", "Back");
        Card toAnswer = sched.getCard();

        //act
        Card siblingBuried = burySiblings(sched, toAnswer);
        Card manuallyBuriedCard = buryManually(sched, toAnswer.getId());

        //perform the search
        List<Long> buriedCards = new Finder(getCol()).findCards(searchQuery, false);

        //assert
        assertThat("A manually buried card should be returned", buriedCards, hasItem(manuallyBuriedCard.getId()));
        assertThat("A sibling buried card should be returned", buriedCards, hasItem(siblingBuried.getId()));
        assertThat("sibling and manually buried should be the only cards returned", buriedCards, hasSize(2));
    }


    private void enableBurySiblings() {
        getCol().getDecks().allConf().get(0).getNew().putBury(true);
    }


    @NonNull
    private Card burySiblings(SchedV2 sched, Card toManuallyBury) {
        sched.answerCard(toManuallyBury, 1);
        Card siblingBuried = new Note(getCol(), toManuallyBury.getNid()).cards().get(1);
        assertThat(siblingBuried.getQueue(), is(Consts.QUEUE_TYPE_SIBLING_BURIED));
        return siblingBuried;
    }


    @NonNull
    private Card buryManually(SchedV2 sched, long id) {
        sched.buryCards(new long[] { id }, true);
        Card manuallyBuriedCard = new Card(getCol(), id);
        assertThat(manuallyBuriedCard.getQueue(), is(Consts.QUEUE_TYPE_MANUALLY_BURIED));
        return manuallyBuriedCard;
    }
}
