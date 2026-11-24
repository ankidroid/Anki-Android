// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.annotation.WorkerThread
import com.ichi2.anki.libanki.Collection

/** Whether tag input can be submitted, with rejection reasons for the UI to localize. */
internal enum class TagInputValidation { VALID, EMPTY, EMPTY_COMPONENT, UNCHANGED, ALREADY_EXISTS }

/** Rejects empty components, unchanged names and collisions while allowing changes to the source's spelling. */
@WorkerThread
internal fun Collection.validateTagRename(
    source: TagName,
    text: String,
    tagExists: (TagName) -> Boolean,
): TagInputValidation {
    val candidate = TagName.build(text.trim()) ?: return TagInputValidation.EMPTY
    // DEFECT Older Android NFC tables can miss compositions that Anki applies,
    // so renaming to an apparently free destination can merge tags.
    val normalizedText = candidate.value.split("::").joinToString("::", transform = TagName::normalizeComponent)
    // Tag registration normalizes the rename's output again. Compare that spelling and identity.
    val normalized = TagName.build(normalizedText) ?: return TagInputValidation.EMPTY_COMPONENT
    val spelling = normalized.normalizedSpelling()
    return when {
        // Stripping control characters can expose additional separators during normalization.
        normalizedText.split("::").any { TagName.normalizeComponent(it).isEmpty() } -> TagInputValidation.EMPTY_COMPONENT
        // DEFECT After an external case-only rename, this check can reject restoring the old spelling
        // and accept keeping the current spelling.
        candidate.value == source.value || spelling == source.value -> TagInputValidation.UNCHANGED
        normalized == source && spellingWithRegisteredParent(spelling) == source.value -> TagInputValidation.UNCHANGED
        // DEFECT Android and Anki can fold Unicode differently, so this check can reject a free
        // destination or allow an occupied one.
        normalized != source && tagExists(normalized) -> TagInputValidation.ALREADY_EXISTS
        else -> TagInputValidation.VALID
    }
}

/** Mirror the backend's preference for the closest parent that is registered in the collection. */
private fun Collection.spellingWithRegisteredParent(spelling: String): String {
    var parent = spelling.substringBeforeLast("::", "")
    while (parent.isNotEmpty()) {
        val storedParent =
            db.query("SELECT tag FROM tags WHERE tag = ?", parent).use { rows ->
                if (rows.moveToFirst()) rows.getString(0) else null
            }
        if (storedParent != null) return storedParent + spelling.substring(parent.length)
        parent = parent.substringBeforeLast("::", "")
    }
    return spelling
}
