/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bytehamster.lib.preferencesearch.PreferenceItem
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.getJavaFieldAsAccessible
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class SettingsSearchBarTest : RobolectricTest() {

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `All indexed XML resIDs lead to the correct fragments on getFragmentFromXmlRes`() {
        // TODO try mocking the activity
        val preferencesActivity = getPreferencesActivity()
        val searchConfig = SearchConfiguration(preferencesActivity)
        HeaderFragment.configureSearchBar(preferencesActivity, searchConfig)

        // Use reflection to access some private fields
        val filesToIndexField = getJavaFieldAsAccessible(SearchConfiguration::class.java, "filesToIndex")
        val searchItemResIdField = getJavaFieldAsAccessible(SearchConfiguration.SearchIndexItem::class.java, "resId")
        val preferencesToIndexField = getJavaFieldAsAccessible(SearchConfiguration::class.java, "preferencesToIndex")
        val prefItemResIdField = getJavaFieldAsAccessible(PreferenceItem::class.java, "resId")

        // Get the resIds of the files indexed with `SearchConfiguration.index`
        val filesToIndex = filesToIndexField.get(searchConfig) as ArrayList<SearchConfiguration.SearchIndexItem>
        val filesResIds = filesToIndex.map {
            searchItemResIdField.get(it)
        }

        // Get the resIds of preferences indexed with `SearchConfiguration.indexItem`
        val preferencesToIndex = preferencesToIndexField.get(searchConfig) as ArrayList<PreferenceItem>
        val prefItemsResIds = preferencesToIndex.map {
            prefItemResIdField.get(it)
        }

        // Join both lists
        val allResIds = filesResIds.plus(prefItemsResIds)
            .distinct() as List<Int>

        // Check if all indexed XML resIDs lead to the correct fragments on getFragmentFromXmlRes
        for (resId in allResIds) {
            val fragment = Preferences.getFragmentFromXmlRes(resId)

            assertNotNull(fragment)
            assertThat(
                "${targetContext.resources.getResourceName(resId)} should match the preferenceResource of ${fragment::class.simpleName}",
                fragment.preferenceResource,
                equalTo(resId)
            )
        }
    }

    private fun getPreferencesActivity(): Preferences {
        return Robolectric.buildActivity(Preferences::class.java)
            .create().start().resume().get()
    }
}
