// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

/** Whether tag input can be submitted, with rejection reasons for the UI to localize. */
internal enum class TagInputValidation { VALID, EMPTY, EMPTY_COMPONENT, UNCHANGED, ALREADY_EXISTS }

/** Rejects empty components, unchanged names and collisions while allowing changes to the source's spelling. */
internal fun validateTagRename(
    source: TagName,
    text: String,
    tagExists: (TagName) -> Boolean,
): TagInputValidation {
    val candidate = TagName.build(text.trim()) ?: return TagInputValidation.EMPTY
    return when {
        candidate.value.split("::").any { it.isBlank() } -> TagInputValidation.EMPTY_COMPONENT
        candidate.value == source.value -> TagInputValidation.UNCHANGED
        candidate != source && tagExists(candidate) -> TagInputValidation.ALREADY_EXISTS
        else -> TagInputValidation.VALID
    }
}
