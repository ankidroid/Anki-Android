/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tags

/** Unwraps [TagListItem.fullTag] to a plain [String] for test assertions */
internal val TagListItem.fullTagName: String get() = fullTag.value

internal val ManageTagsState.Loaded.visibleTagNames: List<String>
    get() = visibleNodes.map { it.fullTagName }

internal fun ManageTagsViewModel.toggleCollapsed(tag: String) = toggleCollapsed(TagName(tag))

internal fun ManageTagsViewModel.removeTag(tag: String) = removeTag(TagName(tag))

internal fun ManageTagsViewModel.renameTag(
    oldName: String,
    newName: String,
) = renameTag(TagName(oldName), TagName(newName))

/** Creates a [TagListItem] from a plain [String] tag */
internal fun tagListItem(
    fullTag: String,
    displayName: String,
    level: Int,
    hasChildren: Boolean,
    collapsed: Boolean,
) = TagListItem(
    fullTag = TagName(fullTag),
    displayName = displayName,
    level = level,
    hasChildren = hasChildren,
    collapsed = collapsed,
)
