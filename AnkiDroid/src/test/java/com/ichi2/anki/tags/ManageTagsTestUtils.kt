// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

/** Unwraps [TagListItemState.fullTag] to a plain [String] for test assertions */
internal val TagListItemState.fullTagName: String get() = fullTag.value

internal val ManageTagsState.Content.visibleTagNames: List<String>
    get() = visibleNodes.map { it.fullTagName }

internal fun ManageTagsViewModel.toggleCollapsed(tag: String) = toggleCollapsed(TagName.asValid(tag))

internal fun ManageTagsViewModel.removeTag(tag: String) = removeTag(TagName.asValid(tag))

internal fun ManageTagsViewModel.renameTag(
    oldName: String,
    newName: String,
) = renameTag(TagName.asValid(oldName), TagName.asValid(newName))

/** Creates a [TagListItemState] from a plain [String] tag */
internal fun tagListItem(
    fullTag: String,
    displayName: String,
    level: Int,
    hasChildren: Boolean,
    collapsed: Boolean,
) = TagListItemState(
    fullTag = TagName.asValid(fullTag),
    displayName = displayName,
    level = level,
    hasChildren = hasChildren,
    collapsed = collapsed,
)

fun TagName.Companion.asValid(name: String) = TagName.build(name)!!
