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

import android.content.Context
import android.util.AttributeSet
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceActionView

/**
 * ActionView class to search through preferences
 */
class PreferencesSearchView : SearchPreferenceActionView {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setSearchConfiguration(searchConfiguration: SearchConfiguration) {
        this.searchConfiguration = searchConfiguration
        this.searchConfiguration.setSearchBarEnabled(false)
    }

    // Close the SearchPreferenceFragment in case the user taps on the toolbar's back button
    override fun onActionViewCollapsed() {
        cancelSearch()
    }

    override fun onActionViewExpanded() {
        isIconified = false
        super.onActionViewExpanded()
    }
}
