/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.robolectric.RuntimeEnvironment
import timber.log.Timber
import kotlin.math.ceil

/** [block] runs with a runtime qualifier emulating a split-pane display */
fun withSplitPaneUi(block: () -> Unit) = withQualifier("sw700dp", block)

fun withQualifier(
    newQualifier: String,
    block: () -> Unit,
) {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    try {
        Timber.d("Adding '$newQualifier' to qualifiers $qualifiers")
        RuntimeEnvironment.setQualifiers("+$newQualifier")
        block()
    } finally {
        Timber.d("Resetting qualifiers to $qualifiers")
        RuntimeEnvironment.setQualifiers(qualifiers)
    }
}

/** [block] runs with a runtime qualifier emulating a split-pane display */
suspend fun withSplitPaneUiAsync(block: suspend () -> Unit) = withQualifierAsync("sw700dp", block)

suspend fun withQualifierAsync(
    newQualifier: String,
    block: suspend () -> Unit,
) {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    try {
        Timber.d("Adding '$newQualifier' to qualifiers $qualifiers")
        RuntimeEnvironment.setQualifiers("+$newQualifier")
        block()
    } finally {
        Timber.d("Resetting qualifiers to $qualifiers")
        RuntimeEnvironment.setQualifiers(qualifiers)
    }
}

/** Height of the screen used to measure a list */
private val MAX_LIST_HEIGHT = 10000.dp

/**
 * Launches [intent] with a full-height screen: tall enough to show all of the list returned
 * by [listView]. The device height is kept if the list already fits.
 *
 * @param setup run after each launch, before [block]
 */
fun <A : Activity> launchForFullHeightScreenshot(
    intent: Intent,
    listView: (A) -> RecyclerView,
    setup: (A) -> Unit = {},
    block: (A) -> Unit,
) {
    val deviceQualifiers = RuntimeEnvironment.getQualifiers()
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deviceHeight = context.resources.configuration.screenHeightDp.dp
    try {
        // measure the list in a tall window, then relaunch at its height
        setScreenHeight(MAX_LIST_HEIGHT)
        var listHeight = 0.dp
        launchAndCheckListFits(intent, listView, setup) { _, list -> listHeight = list.screenHeightToFit() }
        if (listHeight > deviceHeight) {
            setScreenHeight(listHeight)
        } else {
            RuntimeEnvironment.setQualifiers(deviceQualifiers)
        }
        launchAndCheckListFits(intent, listView, setup) { activity, _ -> block(activity) }
    } finally {
        RuntimeEnvironment.setQualifiers(deviceQualifiers)
    }
}

private fun <A : Activity> launchAndCheckListFits(
    intent: Intent,
    listView: (A) -> RecyclerView,
    setup: (A) -> Unit,
    block: (A, RecyclerView) -> Unit,
) {
    ActivityScenario.launch<A>(intent).use { scenario ->
        scenario.onActivity { activity ->
            setup(activity)
            advanceRobolectricLooper() // apply layout changes from setup
            val list = listView(activity)
            check(!list.canScrollVertically(1)) {
                "${activity::class.simpleName}: list does not fit a ${activity.resources.configuration.screenHeightDp}dp screen"
            }
            block(activity, list)
        }
    }
}

private fun setScreenHeight(height: Dp) {
    val qualifiers =
        RuntimeEnvironment.getQualifiers().split("-").mapNotNull { qualifier ->
            when {
                // `+h...dp` is avoided: Robolectric then rebuilds the display from pixels, losing
                // up to 1dp of width per call. Orientation is dropped so a tall screen is not
                // swapped to landscape.
                qualifier.matches(Regex("h\\d+dp")) -> "h${height.dp.toInt()}dp"
                qualifier == "port" || qualifier == "land" -> null
                else -> qualifier
            }
        }
    RuntimeEnvironment.setQualifiers(qualifiers.joinToString("-"))
}

/** The screen height showing every item. Every item must already be laid out */
private fun RecyclerView.screenHeightToFit(): Dp {
    val top = IntArray(2).also { getLocationOnScreen(it) }[1]
    val bottom = top + paddingTop + computeVerticalScrollRange() + paddingBottom
    return (ceil(bottom / resources.displayMetrics.density) + 1).dp // +1: rounding
}
