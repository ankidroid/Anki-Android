// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

/** A language identifier used by Anki's backend for translations. */
@JvmInline
value class AnkiBackendLanguage(
    val value: String,
) {
    companion object {
        /** Maps app language aliases to the codes expected by the backend. */
        fun fromLanguageTag(languageTag: String): AnkiBackendLanguage =
            AnkiBackendLanguage(
                when (languageTag) {
                    "heb" -> "he"
                    "ind" -> "id"
                    "tgl" -> "tl"
                    "hi" -> "hi-IN"
                    else -> languageTag
                },
            )
    }
}
