//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.api

/**
 * Definitions of the basic model
 */
internal class BasicModel {
    companion object {
        @JvmField
        var FIELDS = arrayOf("Front", "Back")
        // List of card names that will be used in AnkiDroid (one for each direction of learning)
        @JvmField
        val CARD_NAMES = arrayOf("Card 1")
        // Template for the question of each card
        @JvmField
        val QFMT = arrayOf("{{Front}}")
        @JvmField
        val AFMT = arrayOf("{{FrontSide}}\n\n<hr id=\"answer\">\n\n{{Back}}")
    }
}
