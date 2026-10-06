// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

/**
 * Bundled SvelteKit routes exposed by AnkiDroid.
 */
internal enum class SvelteKitPage(
    val route: String,
) {
    GRAPHS("graphs"),
    CONGRATS("congrats"),
    CARD_INFO("card-info"),
    CHANGE_NOTETYPE("change-notetype"),
    DECK_OPTIONS("deck-options"),
    IMPORT_ANKI_PACKAGE("import-anki-package"),
    IMPORT_CSV("import-csv"),
    IMPORT_PAGE("import-page"),
    IMAGE_OCCLUSION("image-occlusion"),
    ;

    companion object {
        /** Matches the first segment of a URL path, allowing an optional leading slash. */
        fun fromPath(path: String): SvelteKitPage? {
            val route = path.removePrefix("/").substringBefore("/")
            return entries.find { it.route == route }
        }
    }
}
