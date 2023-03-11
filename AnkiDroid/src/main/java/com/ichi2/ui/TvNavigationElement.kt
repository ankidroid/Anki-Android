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
package com.ichi2.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ichi2.anki.NavigationDrawerActivity
import timber.log.Timber

/** Hack to allow the navigation and options menu to appear when on a TV
 * This is a view to handle dispatchUnhandledMove without using onKeyUp/Down
 * (which interferes with other view events)  */
class TvNavigationElement : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        Timber.d("onFocusChanged %d", direction)
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun dispatchUnhandledMove(focused: View, direction: Int): Boolean {
        Timber.d("dispatchUnhandledMove %d", direction)
        val activity = activity ?: return super.dispatchUnhandledMove(focused, direction)
        if (direction == FOCUS_LEFT && activity is NavigationDrawerActivity) {
            // COULD_BE_BETTER: This leaves focus on the top item when navigation occurs.
            val navigationDrawerActivity = activity
            navigationDrawerActivity.toggleDrawer()
            navigationDrawerActivity.focusNavigation()
            return true
        }
        if (direction == FOCUS_RIGHT) {
            Timber.d("Opening options menu")
            // COULD_BE_BETTER: This crashes inside the framework if right is pressed on the
            openOptionsMenu(activity)
            return true
        }
        return super.dispatchUnhandledMove(focused, direction)
    }

    @SuppressLint("RestrictedApi")
    private fun openOptionsMenu(activity: AppCompatActivity) {
        // This occasionally glitches graphically on my emulator
        val supportActionBar = activity.supportActionBar
        supportActionBar?.openOptionsMenu()
    }

    private val activity: AppCompatActivity?
        get() {
            var context = context
            while (context is ContextWrapper) {
                if (context is AppCompatActivity) {
                    return context
                }
                context = context.baseContext
            }
            return null
        }
}
