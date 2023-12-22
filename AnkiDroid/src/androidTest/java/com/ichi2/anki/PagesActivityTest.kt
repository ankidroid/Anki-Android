/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

@file:Suppress("UnusedReceiverParameter")

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.pages.CardInfo.Companion.toIntent
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.anki.pages.PageFragment
import com.ichi2.anki.pages.PagesActivity
import com.ichi2.anki.pages.Statistics
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Card
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assume.assumeThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@NeedsTest("extend this for all activities - Issue 15009")
class PagesActivityTest : InstrumentedTest() {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    var intentBuilder: (PagesActivityTest.(Context) -> Intent)? = null

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var name: String? = null

    var card: Card? = null

    @Test
    fun activityOpens() {
        val intent = intentBuilder!!.invoke(this, testContext)
        ActivityScenario.launch<PagesActivity>(intent).use { activity ->
            // this can fail on a real device if the screen is off
            assertThat("state is RESUMED", activity.state == Lifecycle.State.RESUMED)
        }
        card?.let {
            col.backend.removeNotes(noteIds = listOf(it.nid), cardIds = listOf(it.id))
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{1}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<out Any>> {
            /** See [PageFragment] */
            val intents =
                listOf<Pair<PagesActivityTest.(Context) -> Intent, String>>(
                    Pair(PagesActivityTest::getStatistics, "Statistics"),
                    Pair(PagesActivityTest::getCardInfo, "CardInfo"),
                    Pair(PagesActivityTest::getCongratsPage, "CongratsPage"),
                    Pair(PagesActivityTest::getDeckOptions, "DeckOptions"),
                    // the following need a file path
                    Pair(PagesActivityTest::needsPath, "AnkiPackageImporterFragment"),
                    Pair(PagesActivityTest::needsPath, "CsvImporter"),
                    Pair(PagesActivityTest::needsPath, "ImageOcclusion"),
                )

            return intents.map { arrayOf(it.first, it.second) }
        }
    }
}

fun PagesActivityTest.getStatistics(context: Context): Intent {
    return Statistics.getIntent(context)
}

fun PagesActivityTest.getCardInfo(context: Context): Intent {
    return addNoteUsingBasicModel().firstCard().let { card ->
        this.card = card
        CardInfoDestination(card.id).toIntent(context)
    }
}

fun PagesActivityTest.getCongratsPage(context: Context): Intent {
    return addNoteUsingBasicModel().firstCard().let { card ->
        this.card = card
        CardInfoDestination(card.id).toIntent(context)
    }
}

fun PagesActivityTest.getDeckOptions(context: Context): Intent {
    return DeckOptions.getIntent(context, col.decks.allNamesAndIds().first().id)
}

fun PagesActivityTest.needsPath(
    @Suppress("UNUSED_PARAMETER") context: Context,
): Intent {
    assumeThat("not implemented: path needed", false, equalTo(true))
    TODO()
}
