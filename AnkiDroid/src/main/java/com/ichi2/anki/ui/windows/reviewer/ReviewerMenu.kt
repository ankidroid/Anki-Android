/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
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
package com.ichi2.anki.ui.windows.reviewer

import android.view.Menu
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.ReviewerMenuView
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.ext.menu
import com.ichi2.anki.utils.ext.removeSubMenu
import com.ichi2.utils.setPaddedIcon

fun ReviewerMenuView.setup(
    lifecycle: Lifecycle,
    viewModel: ReviewerViewModel,
) {
    if (isEmpty()) {
        isVisible = false
        return
    }

    viewModel.flagFlow
        .flowWithLifecycle(lifecycle)
        .collectLatestIn(lifecycle.coroutineScope) { flagCode ->
            findItem(ViewerAction.FLAG_MENU.menuId)?.setPaddedIcon(context, flagCode.drawableRes)
        }

    val markItem = findItem(ViewerAction.MARK.menuId)
    viewModel.isMarkedFlow
        .flowWithLifecycle(lifecycle)
        .collectLatestIn(lifecycle.coroutineScope) { isMarked ->
            if (isMarked) {
                markItem?.setPaddedIcon(context, R.drawable.ic_star)
                markItem?.setTitle(R.string.menu_unmark_note)
            } else {
                markItem?.setPaddedIcon(context, R.drawable.ic_star_border_white)
                markItem?.setTitle(R.string.menu_mark_note)
            }
        }

    val undoItem = findItem(ViewerAction.UNDO.menuId)
    viewModel.undoLabelFlow
        .flowWithLifecycle(lifecycle)
        .collectLatestIn(lifecycle.coroutineScope) { label ->
            undoItem?.title = label ?: CollectionManager.TR.undoUndo()
            undoItem?.isEnabled = label != null
        }

    val redoItem = findItem(ViewerAction.REDO.menuId)
    viewModel.redoLabelFlow
        .flowWithLifecycle(lifecycle)
        .collectLatestIn(lifecycle.coroutineScope) { label ->
            redoItem?.title = label ?: CollectionManager.TR.undoRedo()
            redoItem?.isEnabled = label != null
        }

    val suspendItem = findItem(ViewerAction.SUSPEND_MENU.menuId) ?: return
    val suspendFlow = viewModel.canSuspendNoteFlow.flowWithLifecycle(lifecycle)
    suspendFlow.collectLatestIn(lifecycle.coroutineScope) { canSuspendNote ->
        if (canSuspendNote) {
            if (suspendItem.hasSubMenu()) return@collectLatestIn
            suspendItem.setTitle(ViewerAction.SUSPEND_MENU.titleRes)
            val submenu =
                SubMenuBuilder(context, suspendItem.menu, suspendItem).apply {
                    add(Menu.NONE, ViewerAction.SUSPEND_NOTE.menuId, Menu.NONE, ViewerAction.SUSPEND_NOTE.titleRes)
                    add(Menu.NONE, ViewerAction.SUSPEND_CARD.menuId, Menu.NONE, ViewerAction.SUSPEND_CARD.titleRes)
                }
            suspendItem.setSubMenu(submenu)
        } else {
            suspendItem.removeSubMenu()
            suspendItem.setTitle(ViewerAction.SUSPEND_CARD.titleRes)
        }
    }

    val buryItem = findItem(ViewerAction.BURY_MENU.menuId) ?: return
    val flow = viewModel.canBuryNoteFlow.flowWithLifecycle(lifecycle)
    flow.collectLatestIn(lifecycle.coroutineScope) { canBuryNote ->
        if (canBuryNote) {
            if (buryItem.hasSubMenu()) return@collectLatestIn
            buryItem.setTitle(ViewerAction.BURY_MENU.titleRes)
            val submenu =
                SubMenuBuilder(context, buryItem.menu, buryItem).apply {
                    add(Menu.NONE, ViewerAction.BURY_NOTE.menuId, Menu.NONE, ViewerAction.BURY_NOTE.titleRes)
                    add(Menu.NONE, ViewerAction.BURY_CARD.menuId, Menu.NONE, ViewerAction.BURY_CARD.titleRes)
                }
            buryItem.setSubMenu(submenu)
        } else {
            buryItem.removeSubMenu()
            buryItem.setTitle(ViewerAction.BURY_CARD.titleRes)
        }
    }
}
