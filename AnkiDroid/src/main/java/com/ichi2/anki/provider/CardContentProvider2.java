package com.ichi2.anki.provider;

import com.ichi2.anki.FlashCardsContract;

/**
 * Content Provider designed for Android SDK 23 and above
 */
public final class CardContentProvider2 extends BaseCardContentProvider {
    private static final int VER = 2;
    static {
        // Here you can see all the URIs at a glance
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes", NOTES);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes/#", NOTES_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes/#/cards", NOTES_ID_CARDS);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes/#/cards/#", NOTES_ID_CARDS_ORD);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models", MODELS);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models/*", MODELS_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models/*/templates", MODELS_ID_TEMPLATES);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models/*/templates/#", MODELS_ID_TEMPLATES_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "schedule/", SCHEDULE);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "decks/", DECKS);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "decks/#", DECKS_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "selected_deck/", DECK_SELECTED);
    }
}