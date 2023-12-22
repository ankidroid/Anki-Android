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

package com.ichi2.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.graphics.drawable.DrawableWrapperCompat
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.view.forEach
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.convertDpToPixel

private fun Menu.forEachOverflowItemRecursive(block: (MenuItem) -> Unit) {
    (this as? MenuBuilder)?.flagActionItems()

    forEach { item ->
        if ((item as? MenuItemImpl)?.isActionButton == false) block(item)
        item.subMenu?.forEachOverflowItemRecursive(block)
    }
}

/**
 * Recursively increase horizontal icon padding for the items of the given menu,
 * so that the icon visually appears at the same distance from the starting edge of the popup
 * as from the top and the bottom edges, as well as from the label.
 * Has no effect for items that have no icon, or for items this has processed before.
 */
fun Context.increaseHorizontalPaddingOfOverflowMenuIcons(menu: Menu) {
    val extraPadding = convertDpToPixel(5f, this).toInt()

    class Wrapper(drawable: Drawable) : DrawableWrapperCompat(drawable) {
        override fun mutate() = drawable!!.mutate() // DrawableWrapperCompat fails to delegate this

        override fun getIntrinsicWidth() = super.getIntrinsicWidth() + extraPadding * 2

        override fun setBounds(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.setBounds(left + extraPadding, top, right - extraPadding, bottom)
        }
    }

    menu.forEachOverflowItemRecursive { item ->
        item.icon?.let { icon ->
            if (icon !is Wrapper) item.icon = Wrapper(icon)
        }
    }
}

/**
 * Recursively mutates and tints the icons of the items of the given overflow or popup menu
 * with the color [R.attr.overflowAndPopupMenuIconColor] that is specified in the theme.
 * Has no effect for items that have no icon.
 */
fun Context.tintOverflowMenuIcons(
    menu: Menu,
    skipIf: ((MenuItem) -> Boolean)? = null,
) {
    val iconColor = MaterialColors.getColor(this, R.attr.overflowAndPopupMenuIconColor, 0)

    menu.forEachOverflowItemRecursive { item ->
        if (skipIf == null || !skipIf(item)) {
            item.icon?.mutate()?.setTint(iconColor)
        }
    }
}
