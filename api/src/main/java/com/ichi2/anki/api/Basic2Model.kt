//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.api;

/**
 * Definitions of the basic with reverse model
 */
class Basic2Model {
    public static final String[] FIELDS = {"Front","Back"};
    // List of card names that will be used in AnkiDroid (one for each direction of learning)
    public static final String[] CARD_NAMES = {"Card 1", "Card 2"};
    // Template for the question of each card
    static final String[] QFMT = {"{{Front}}", "{{Back}}"};
    static final String[] AFMT = {"{{FrontSide}}\n\n<hr id=\"answer\">\n\n{{Back}}",
            "{{FrontSide}}\n\n<hr id=\"answer\">\n\n{{Front}}"};
}
