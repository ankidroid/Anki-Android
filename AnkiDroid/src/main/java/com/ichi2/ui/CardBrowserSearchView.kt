/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.SearchView

class CardBrowserSearchView : SearchView {
    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    /** Whether an action to set text should be ignored  */
    private var mIgnoreValueChange = false

    /** Whether an action to set text should be ignored  */
    fun shouldIgnoreValueChange(): Boolean {
        return mIgnoreValueChange
    }

    override fun onActionViewCollapsed() {
        try {
            mIgnoreValueChange = true
            super.onActionViewCollapsed()
        } finally {
            mIgnoreValueChange = false
        }
    }

    override fun onActionViewExpanded() {
        try {
            mIgnoreValueChange = true
            super.onActionViewExpanded()
        } finally {
            mIgnoreValueChange = false
        }
    }

    override fun setQuery(query: CharSequence, submit: Boolean) {
        if (mIgnoreValueChange) {
            return
        }
        super.setQuery(query, submit)
    }
}
