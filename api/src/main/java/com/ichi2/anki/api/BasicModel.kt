//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.api;

/**
 * Definitions of the basic model
 */
class BasicModel {
    public static final String[] FIELDS = {"Front","Back"};
    // List of card names that will be used in AnkiDroid (one for each direction of learning)
    public static final String[] CARD_NAMES = {"Card 1"};
    // Template for the question of each card
    static final String[] QFMT = {"{{Front}}"};
    static final String[] AFMT = {"{{FrontSide}}\n\n<hr id=\"answer\">\n\n{{Back}}"};
}
