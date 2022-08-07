/*
 Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

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
package com.ichi2.anki.stats

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.stats.OverviewStatsBuilder.OverviewStats.AnswerButtonsOverview
import com.ichi2.libanki.stats.Stats
import com.ichi2.utils.KotlinCleanup
import junit.framework.TestCase.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("Add @Language to HTML")
class OverviewStatsBuilderTest : RobolectricTest() {
    @Test
    fun testGetPercentage() {
        val testAnswerButtonsOverview = AnswerButtonsOverview()
        assertEquals(testAnswerButtonsOverview.percentage.toInt(), 0)

        testAnswerButtonsOverview.correct = 15
        testAnswerButtonsOverview.total = 50
        assertEquals(testAnswerButtonsOverview.percentage, 30.0)

        testAnswerButtonsOverview.correct = 50
        testAnswerButtonsOverview.total = 100
        assertEquals(testAnswerButtonsOverview.percentage, 50.0)
    }

    @Test
    @Config(qualifiers = "en")
    fun testInfoHtmlStringMonth() {
        val statsTester = OverviewStatsBuilder(
            WebView(targetContext),
            col,
            42L,
            Stats.AxisType.TYPE_MONTH
        )
        val html = statsTester.createInfoHtmlString()
        assertEquals(
            html,
            """
     <center><style>
     h1, h3 { margin-bottom: 0; margin-top: 1em; text-transform: capitalize; }
     .pielabel { text-align:center; padding:0px; color:white; }
     body {color:#FFFFFF;}
     </style><h1>Today</h1>Studied <b>0 cards</b> in <b>0 minutes</b> today<br>Again count: <b>0</b><br>Learn: <b>0</b>, review: <b>0</b>, relearn: <b>0</b>, filtered: <b>0</b><br>No mature cards were studied today<h1>1 month</h1><h3>FORECAST</h3>Total: <b>0</b> reviews<br>Average: <b>0.0</b> reviews/day<br>Due tomorrow: <b>0</b><br><h3>REVIEW COUNT</h3>Days studied: <b>0%</b> (0 of 30)<br>Total: <b>0</b> reviews<br>Average for days studied: <b>0.0</b> reviews/day<br>If you studied every day: <b>0.0</b> reviews/day<br><h3>REVIEW TIME</h3>Days studied: <b>0%</b> (0 of 30)<br>Total: <b>0</b> minutes<br>Average for days studied: <b>0.0</b> minutes/day<br>If you studied every day: <b>0.0</b> minutes/day<br>Average answer time: <b>0.0s</b> (<b>0.00</b> cards/minute)<br><h3>ADDED</h3>Total: <b>0</b> cards<br>Average: <b>0.0</b> cards/day<br><h3>INTERVALS</h3>Average interval: <b>0.0</b> hours<br>Longest interval: <b>0.0</b> hours<h3>ANSWER BUTTONS</h3>Learning: <b>0.00%</b> correct (0 of 0)<br>Young: <b>0.00%</b> correct (0 of 0)<br>Mature: <b>0.00%</b> correct (0 of 0)<h3>CARD TYPES</h3>Total cards: <b>0</b><br>Total notes: <b>0</b><br>Lowest ease: <b>0%</b><br>Average ease: <b>0%</b><br>Highest ease: <b>0%</b></center>
            """.trimIndent()
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun testInfoHtmlStringYear() {
        val statsTester = OverviewStatsBuilder(
            WebView(targetContext),
            col,
            42L,
            Stats.AxisType.TYPE_YEAR
        )
        val html = statsTester.createInfoHtmlString()
        assertEquals(
            html,
            """
     <center><style>
     h1, h3 { margin-bottom: 0; margin-top: 1em; text-transform: capitalize; }
     .pielabel { text-align:center; padding:0px; color:white; }
     body {color:#FFFFFF;}
     </style><h1>Today</h1>Studied <b>0 cards</b> in <b>0 minutes</b> today<br>Again count: <b>0</b><br>Learn: <b>0</b>, review: <b>0</b>, relearn: <b>0</b>, filtered: <b>0</b><br>No mature cards were studied today<h1>1 year</h1><h3>FORECAST</h3>Total: <b>0</b> reviews<br>Average: <b>0.0</b> reviews/day<br>Due tomorrow: <b>0</b><br><h3>REVIEW COUNT</h3>Days studied: <b>0%</b> (0 of 365)<br>Total: <b>0</b> reviews<br>Average for days studied: <b>0.0</b> reviews/day<br>If you studied every day: <b>0.0</b> reviews/day<br><h3>REVIEW TIME</h3>Days studied: <b>0%</b> (0 of 365)<br>Total: <b>0</b> minutes<br>Average for days studied: <b>0.0</b> minutes/day<br>If you studied every day: <b>0.0</b> minutes/day<br>Average answer time: <b>0.0s</b> (<b>0.00</b> cards/minute)<br><h3>ADDED</h3>Total: <b>0</b> cards<br>Average: <b>0.0</b> cards/day<br><h3>INTERVALS</h3>Average interval: <b>0.0</b> hours<br>Longest interval: <b>0.0</b> hours<h3>ANSWER BUTTONS</h3>Learning: <b>0.00%</b> correct (0 of 0)<br>Young: <b>0.00%</b> correct (0 of 0)<br>Mature: <b>0.00%</b> correct (0 of 0)<h3>CARD TYPES</h3>Total cards: <b>0</b><br>Total notes: <b>0</b><br>Lowest ease: <b>0%</b><br>Average ease: <b>0%</b><br>Highest ease: <b>0%</b></center>
            """.trimIndent()
        )
    }

    @Test
    @Config(qualifiers = "en")
    fun testInfoHtmlStringLife() {
        val statsTester = OverviewStatsBuilder(
            WebView(targetContext),
            col,
            42L,
            Stats.AxisType.TYPE_LIFE
        )
        val html = statsTester.createInfoHtmlString()
        assertEquals(
            html,
            """
     <center><style>
     h1, h3 { margin-bottom: 0; margin-top: 1em; text-transform: capitalize; }
     .pielabel { text-align:center; padding:0px; color:white; }
     body {color:#FFFFFF;}
     </style><h1>Today</h1>Studied <b>0 cards</b> in <b>0 minutes</b> today<br>Again count: <b>0</b><br>Learn: <b>0</b>, review: <b>0</b>, relearn: <b>0</b>, filtered: <b>0</b><br>No mature cards were studied today<h1>deck life</h1><h3>FORECAST</h3>Total: <b>0</b> reviews<br>Average: <b>0.0</b> reviews/day<br>Due tomorrow: <b>0</b><br><h3>REVIEW COUNT</h3>Days studied: <b>0%</b> (0 of 1)<br>Total: <b>0</b> reviews<br>Average for days studied: <b>0.0</b> reviews/day<br>If you studied every day: <b>0.0</b> reviews/day<br><h3>REVIEW TIME</h3>Days studied: <b>0%</b> (0 of 1)<br>Total: <b>0</b> minutes<br>Average for days studied: <b>0.0</b> minutes/day<br>If you studied every day: <b>0.0</b> minutes/day<br>Average answer time: <b>0.0s</b> (<b>0.00</b> cards/minute)<br><h3>ADDED</h3>Total: <b>0</b> cards<br>Average: <b>0.0</b> cards/day<br><h3>INTERVALS</h3>Average interval: <b>0.0</b> hours<br>Longest interval: <b>0.0</b> hours<h3>ANSWER BUTTONS</h3>Learning: <b>0.00%</b> correct (0 of 0)<br>Young: <b>0.00%</b> correct (0 of 0)<br>Mature: <b>0.00%</b> correct (0 of 0)<h3>CARD TYPES</h3>Total cards: <b>0</b><br>Total notes: <b>0</b><br>Lowest ease: <b>0%</b><br>Average ease: <b>0%</b><br>Highest ease: <b>0%</b></center>
            """.trimIndent()
        )
    }
}
