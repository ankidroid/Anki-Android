/****************************************************************************************
 * Copyright (c) 2022 lukstbit <lukstbit@users.noreply.github.com>                      *
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
package com.ichi2.anki.dialogs

import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ModelEditorContextMenu.ModelEditorContextMenuAction
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelEditorContextMenuTest : InstrumentedTest() {
    private val testDialogTitle = "test editor title"

    @Test
    @Ignore("flaky")
    fun showsAllOptionsIfAboveN() {
        launchFragment(
            fragmentArgs = bundleOf(ModelEditorContextMenu.KEY_LABEL to testDialogTitle),
            themeResId = R.style.Theme_Light
        ) { MockModelEditorContextMenu(isAtLeastAtN = true) }
        onView(withText(testDialogTitle)).check(matches(isDisplayed()))
        ModelEditorContextMenuAction.entries.forEach {
            onView(withText(it.actionTextId)).check(matches(isDisplayed()))
        }
    }

    @Test
    @Ignore("flaky")
    fun doesNotShowLanguageHintOptionIfBelowN() {
        launchFragment(
            fragmentArgs = bundleOf(ModelEditorContextMenu.KEY_LABEL to testDialogTitle),
            themeResId = R.style.Theme_Light
        ) { MockModelEditorContextMenu(isAtLeastAtN = false) }
        onView(withText(testDialogTitle)).check(matches(isDisplayed()))
        // ModelEditorContextMenuAction.AddLanguageHint shouldn't be available
        onView(withText(ModelEditorContextMenuAction.AddLanguageHint.actionTextId)).check(
            doesNotExist()
        )
        // make sure we aren't losing other items besides ModelEditorContextMenuAction.AddLanguageHint
        ModelEditorContextMenuAction.entries
            .filterNot { it == ModelEditorContextMenuAction.AddLanguageHint }.forEach {
                onView(withText(it.actionTextId)).check(matches(isDisplayed()))
            }
    }

    class MockModelEditorContextMenu(
        private val isAtLeastAtN: Boolean,
    ) : ModelEditorContextMenu() {
        override fun isAtLeastAtN(): Boolean = isAtLeastAtN
    }
}
