/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences.reviewer

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.ActionMenuView
import androidx.core.view.size
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import kotlinx.coroutines.launch

/**
 * View for displaying the reviewer menu actions.
 *
 * It works like an [ActionMenuView], but the visible action items are horizontally scrollable by
 * using an [ActionMenuView] inside a [HorizontalScrollView], and another one for the overflown
 * actions.
 *
 * It also initializes itself, which makes possible to see it in Android Studio layout previews.
 *
 * @see [R.layout.reviewer_menu_item]
 */
class ReviewerMenuView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : LinearLayout(context, attrs, defStyleAttr) {
        private val frontMenu: Menu
        private val overflowMenu: Menu

        init {
            val inflater = LayoutInflater.from(context).inflate(R.layout.reviewer_menu_view, this, true)
            frontMenu = inflater.findViewById<ActionMenuView>(R.id.front_menu_view).menu
            overflowMenu =
                inflater.findViewById<ActionMenuView>(R.id.overflow_menu_view).menu.apply {
                    (this as? MenuBuilder)?.setOptionalIconsVisible(true)
                }
            setupMenus()
        }

        fun clear() {
            frontMenu.clear()
            overflowMenu.clear()
        }

        fun isEmpty() = frontMenu.size == 0 && overflowMenu.size == 0

        fun findItem(id: Int): MenuItemImpl? = (frontMenu.findItem(id) ?: overflowMenu.findItem(id)) as? MenuItemImpl

        fun setOnMenuItemClickListener(listener: ActionMenuView.OnMenuItemClickListener) {
            findViewById<ActionMenuView>(R.id.front_menu_view).setOnMenuItemClickListener(listener)
            findViewById<ActionMenuView>(R.id.overflow_menu_view).setOnMenuItemClickListener(listener)
        }

        fun addActions(
            alwaysShow: List<ViewerAction>,
            menuOnly: List<ViewerAction>,
        ) {
            addActionsToMenu(frontMenu, alwaysShow, MenuItem.SHOW_AS_ACTION_ALWAYS)
            addActionsToMenu(overflowMenu, menuOnly, MenuItem.SHOW_AS_ACTION_NEVER)

            val submenuActions = ViewerAction.entries.filter { it.parentMenu != null }
            for (action in submenuActions) {
                val subMenu = findItem(action.parentMenu!!.menuId)?.subMenu ?: continue
                val title = resources.getString(action.titleRes)
                subMenu.add(Menu.NONE, action.menuId, Menu.NONE, title)?.apply {
                    action.drawableRes?.let { setIcon(it) }
                }
            }
        }

        suspend fun setFlagTitles() {
            val submenu = findItem(R.id.action_flag)?.subMenu ?: return
            for ((flag, name) in Flag.queryDisplayNames()) {
                submenu.findItem(flag.id)?.title = name
            }
        }

        private fun addActionsToMenu(
            menu: Menu,
            actions: List<ViewerAction>,
            menuActionType: Int,
        ) {
            val subMenus = ViewerAction.getSubMenus()
            for (action in actions) {
                val title = resources.getString(action.titleRes)
                val menuItem =
                    if (action in subMenus) {
                        menu.addSubMenu(Menu.NONE, action.menuId, Menu.NONE, title).item
                    } else {
                        menu.add(Menu.NONE, action.menuId, Menu.NONE, title)
                    }
                with(menuItem) {
                    action.drawableRes?.let { setIcon(it) }
                    setShowAsAction(menuActionType)
                }
            }
        }

        private fun setupMenus() {
            val menuItems = MenuDisplayType.getMenuItems(context.sharedPrefs(), MenuDisplayType.ALWAYS, MenuDisplayType.MENU_ONLY)
            addActions(menuItems.getValue(MenuDisplayType.ALWAYS), menuItems.getValue(MenuDisplayType.MENU_ONLY))
            // wait until attached to a fragment or activity to launch the coroutine to setup flags
            viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                            setFlagTitles()
                        }
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                },
            )
        }
    }
