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

package com.ichi2.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;

public class CardBrowserSearchView extends SearchView {
    public CardBrowserSearchView(@NonNull Context context) {
        super(context);
    }


    public CardBrowserSearchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }


    public CardBrowserSearchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** Whether an action to set text should be ignored */
    private boolean mIgnoreValueChange = false;

    /** Whether an action to set text should be ignored */
    public boolean shouldIgnoreValueChange() {
        return mIgnoreValueChange;
    }


    @Override
    public void onActionViewCollapsed() {
        try {
            mIgnoreValueChange = true;
            super.onActionViewCollapsed();
        } finally {
            mIgnoreValueChange = false;
        }
    }


    @Override
    public void onActionViewExpanded() {
        try {
            mIgnoreValueChange = true;
            super.onActionViewExpanded();
        } finally {
            mIgnoreValueChange = false;
        }
    }


    @Override
    public void setQuery(CharSequence query, boolean submit) {
        if (mIgnoreValueChange) {
            return;
        }
        super.setQuery(query, submit);
    }
}
