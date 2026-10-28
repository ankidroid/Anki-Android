// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

/** Whether tag input can be submitted, with rejection reasons for the UI to localize. */
internal enum class TagInputValidation { VALID, EMPTY, REJECTED, ALREADY_EXISTS }

/** Rejects unchanged names and collisions while allowing changes to the source's spelling. */
internal fun validateTagRename(
    source: TagName,
    text: String,
    tagExists: (TagName) -> Boolean,
): TagInputValidation {
    val candidate = TagName.build(text.trim()) ?: return TagInputValidation.EMPTY
    return when {
        candidate.value == source.value -> TagInputValidation.REJECTED
        candidate != source && tagExists(candidate) -> TagInputValidation.ALREADY_EXISTS
        else -> TagInputValidation.VALID
    }
}
