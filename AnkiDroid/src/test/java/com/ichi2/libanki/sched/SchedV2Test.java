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
import com.ichi2.testutils.MockTime;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.platform.commons.util.CollectionUtils.getOnlyElement;

@RunWith(AndroidJUnit4.class)
public class SchedV2Test extends RobolectricTest {

    /** Reported by /u/CarelessSecretary9 on reddit: */
    @Test
    public void filteredDeckSchedulingOptionsRegressionTest() {
        getCol().setCrt(1587852900L);
        //30 minutes learn ahead. required as we have 20m delay
        getCol().getConf().put("collapseTime", 1800);

        long homeDeckId = addDeck("Poorretention");

        JSONObject homeDeckConf = getCol().getDecks().confForDid(homeDeckId);
        JSONObject lapse = homeDeckConf.getJSONObject("lapse");

        lapse.put("minInt", 2);
        lapse.put("mult", 0.7d);
        lapse.put("delays", new JSONArray("[20]"));

        ensureLapseMatchesSppliedAnkiDesktopConfig(lapse);

        getCol().flush();

        long dynId = addDynamicDeck("Dyn");

        /*
        >>> pp(self.reviewer.card)
        {'data': '', 'did': 1587939535230, 'due': 0, 'factor': 1300, 'flags': 0, 'id': 1510928829863, 'ivl': 25,
        'lapses': 5, 'left': 1004, 'mod': 1587921512, 'nid': 1510928805161, 'odid': 1587920944107,
        'odue': 0, 'ord': 0, 'queue': 2, 'reps': 22, 'type': 2, 'usn': -1}

         */
        Note n = addNoteUsingBasicModel("Hello", "World");
        Card c = getOnlyElement(n.cards());
        c.setType(Consts.CARD_TYPE_REV);
        c.setQueue(Consts.QUEUE_TYPE_REV);
        c.setIvl(25);
        c.setDue(0);
        c.setLapses(5);
        c.setFactor(1300);
        c.setLeft(1004);
        c.setODid(homeDeckId);
        c.setDid(dynId);
        c.flush();

        SchedV2 v2 = new SchedV2(getCol(), new MockTime(1587928085001L));

        Card schedCard = v2.getCard();
        assertThat(schedCard, Matchers.notNullValue());
        v2.answerCard(schedCard, Consts.BUTTON_ONE);
        assertThat("The lapsed card should now be counted as lrn", v2.mLrnCount, is(1));
        Card after = v2.getCard();
        assertThat("A card should be returned ", after, Matchers.notNullValue());

        /* Data from Anki - pp(self.reviewer.card)
        {'data': '', 'did': 1587939535230, 'due': 1587941137, 'factor': 1300,
        'flags': 0, 'id': 1510928829863, 'ivl': 17, 'lapses': 6, 'left': 1001,
        'mod': 1587939720, 'nid': 1510928805161, 'odid': 1587920944107, 'odue': 0,
        'ord': 0, 'queue': 1, 'reps': 23, 'type': 3, 'usn': -1}
         */
        assertThat(after.getType(), is(Consts.CARD_TYPE_RELEARNING));
        assertThat(after.getQueue(), is(Consts.QUEUE_TYPE_LRN));
        assertThat(after.getLeft(), is(1001));
        assertThat("ivl is reduced by 70%", after.getIvl(), is(17));
        assertThat("One lapse is added", after.getLapses(), is(6));

        assertThat(v2.answerButtons(after), is(4));

        long one = v2.nextIvl(after, Consts.BUTTON_ONE);
        long two = v2.nextIvl(after, Consts.BUTTON_TWO);
        long three = v2.nextIvl(after, Consts.BUTTON_THREE);
        long four = v2.nextIvl(after, Consts.BUTTON_FOUR);

        assertThat("Again should pick the current step", one, is(1200L));      // 20 mins
        assertThat("Repeating single step - 20 minutes * 1.5", two, is(1800L));      // 30 mins
        assertThat("Good should take the reduced interval (25 * 0.7)", three, is(1468800L)); // 17 days
        assertThat("Easy should have a bonus day over good", four, is(1555200L));  // 18 days
    }


    private void ensureLapseMatchesSppliedAnkiDesktopConfig(JSONObject lapse) {
        assertThat(lapse.getInt("minInt"), is(2));
        assertThat(lapse.getDouble("mult"), is(0.7d));
        assertThat(lapse.getJSONArray("delays").length(), is(1));
        assertThat(lapse.getJSONArray("delays").get(0), is(20));

    }


}
