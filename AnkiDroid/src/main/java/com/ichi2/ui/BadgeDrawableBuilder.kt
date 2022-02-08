/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

package com.ichi2.ui

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MenuItem
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.ichi2.anki.R
import timber.log.Timber

class BadgeDrawableBuilder(private val resources: Resources) {
    private var mChar = '\u0000'
    private var mColor: Int? = null
    fun withText(c: Char): BadgeDrawableBuilder {
        mChar = c
        return this
    }

    fun withColor(color: Int?): BadgeDrawableBuilder {
        mColor = color
        return this
    }

    fun replaceBadge(menuItem: MenuItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        Timber.d("Adding badge")
        var originalIcon = menuItem.icon
        if (originalIcon is BadgeDrawable) {
            originalIcon = originalIcon.current
        }
        val badge = BadgeDrawable(originalIcon)
        if (mChar != '\u0000') {
            badge.setText(mChar)
        }
        if (mColor != null) {
            val badgeDrawable: Drawable? = VectorDrawableCompat.create(resources, R.drawable.badge_drawable, null)
            if (badgeDrawable == null) {
                Timber.w("Unable to find badge_drawable - not drawing badge")
                return
            }
            val mutableDrawable = badgeDrawable.mutate()
            mutableDrawable.setTint(mColor!!)
            badge.setBadgeDrawable(mutableDrawable)
            menuItem.icon = badge
        }
    }

    companion object {
        @JvmStatic
        fun removeBadge(menuItem: MenuItem) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return
            }
            val icon = menuItem.icon
            if (icon is BadgeDrawable) {
                menuItem.icon = icon.current
                Timber.d("Badge removed")
            }
        }
    }
}
