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
    // DEFECT Older Android NFC tables can miss compositions that Anki applies,
    // so renaming to an apparently free destination can merge tags.
    val normalizedText = candidate.value.split("::").joinToString("::", transform = TagName::normalizeComponent)
    // Tag registration normalizes the rename's output again. Compare that spelling and identity.
    val normalized = TagName.build(normalizedText) ?: return TagInputValidation.EMPTY_COMPONENT
    return when {
        // Stripping control characters can expose additional separators during normalization.
        normalizedText.split("::").any { TagName.normalizeComponent(it).isEmpty() } -> TagInputValidation.EMPTY_COMPONENT
        // DEFECT Anki preserves registered parent casing, so a parent-only case edit can leave the tag unchanged.
        // DEFECT After an external case-only rename, this check can reject restoring the old spelling
        // and accept keeping the current spelling.
        candidate.value == source.value || normalized.normalizedSpelling() == source.value -> TagInputValidation.UNCHANGED
        // DEFECT Android and Anki can fold Unicode differently, so this check can reject a free
        // destination or allow an occupied one.
        normalized != source && tagExists(normalized) -> TagInputValidation.ALREADY_EXISTS
        else -> TagInputValidation.VALID
    }
}
