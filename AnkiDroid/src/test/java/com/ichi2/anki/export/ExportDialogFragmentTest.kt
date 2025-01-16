/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.export

import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportDialogFragmentTest : RobolectricTest() {
    @Test
    fun `Legacy export checkbox(default false) is shown only for collection and apkg`() {
        launchFragment<ExportDialogFragment>(
            themeResId = R.style.Theme_Light,
        ).onFragment {
            // check legacy checkboxes status for collection export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(click())
            onData(containsString(TR.exportingAnkiCollectionPackage()))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.export_legacy_checkbox_collection))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withId(R.id.export_legacy_checkbox_collection))
                .inRoot(isDialog())
                .check(matches(not(isChecked())))
            onView(withId(R.id.export_legacy_checkbox_apkg))
                .inRoot(isDialog())
                .check(matches(not(isDisplayed())))

            // check legacy checkboxes status for apkg export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(click())
            onData(containsString(TR.exportingAnkiDeckPackage()))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.export_legacy_checkbox_apkg))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withId(R.id.export_legacy_checkbox_apkg))
                .inRoot(isDialog())
                .check(matches(not(isChecked())))
            onView(withId(R.id.export_legacy_checkbox_collection))
                .inRoot(isDialog())
                .check(matches(not(isDisplayed())))

            // checkboxes are not shown for notes export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(click())
            onData(containsString(TR.exportingNotesInPlainText()))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.export_legacy_checkbox_apkg))
                .inRoot(isDialog())
                .check(matches(not(isDisplayed())))
            onView(withId(R.id.export_legacy_checkbox_collection))
                .inRoot(isDialog())
                .check(matches(not(isDisplayed())))

            // checkboxes are not shown for cards export
            onView(withId(R.id.export_type_selector)).inRoot(isDialog()).perform(click())
            onData(containsString(TR.exportingCardsInPlainText()))
                .inAdapterView(withId(R.id.export_type_selector))
                .perform(click())
            onView(withId(R.id.export_legacy_checkbox_apkg))
                .inRoot(isDialog())
                .check(matches(not(isDisplayed())))
            onView(withId(R.id.export_legacy_checkbox_collection))
                .inRoot(isDialog())
                .check(matches(not(isDisplayed())))
        }
    }
}
