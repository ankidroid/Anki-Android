/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardInfoModelTest : RobolectricTest() {
    private var mModel: CardInfo.CardInfoModel? = null

    @Before
    fun setupModel() {
        // using a card from my collection
        val c = addNoteUsingBasicModel("Hello", "World").firstCard()

        // filtering the card did this.
        c.due = -99826
        c.oDue = 705
        c.oDid = 1438357550678L

        /*
        cards table:
        1438962063638,1438962011404,1438953473618,0,1441720656,2949,2,2,-99826,12,1950,14,2,1001,705,1438357550678,0,""
         */

        col.crt = 1381723200
        col.db.executeScript(
            "insert into revlog values (1441720656373,1438962063638,1603,2,12,13,1950,2619,1);" +
                "insert into revlog values (1440617351419,1438962063638,1541,3,13,7,2100,1582,1);" +
                "insert into revlog values (1440007700496,1438962063638,1479,3,7,5,2100,1845,1);" +
                "insert into revlog values (1439562984127,1438962063638,1417,3,5,3,2100,2244,1);" +
                "insert into revlog values (1439329318283,1438962063638,1382,3,3,1,2100,2986,1);" +
                "insert into revlog values (1439243496005,1438962063638,1367,2,1,-600,2100,2212,2);" +
                "insert into revlog values (1439242482555,1438962063638,1367,1,-600,1,2100,3187,1);" +
                "insert into revlog values (1439048190364,1438962063638,1344,2,1,-600,2300,2288,3);" +
                "insert into revlog values (1439047111872,1438962063638,1344,2,-600,-60,2300,2371,3);" +
                "insert into revlog values (1439046902309,1438962063638,1344,1,-60,1,2300,39654,1);" +
                "insert into revlog values (1438984085994,1438962063638,1342,2,1,-600,2500,2648,3);" +
                "insert into revlog values (1438983221721,1438962063638,1337,2,-600,-60,0,2214,3);" +
                "insert into revlog values (1438983131213,1438962063638,1337,1,-60,-60,0,3123,3);" +
                "insert into revlog values (1438983050444,1438962063638,1337,1,-60,-60,0,5282,0);"
        )
        col.save()

        mModel = CardInfo.CardInfoModel.create(col, c)
    }

    @Test
    fun ensureFilteredCardIsMarkedAsSuch() {
        // differs from Anki Desktop - provides date in 1700
        assertThat(mModel!!.due, equalTo("(filtered)"))
    }
}
