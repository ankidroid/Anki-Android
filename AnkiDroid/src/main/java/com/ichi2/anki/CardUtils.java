package com.ichi2.anki;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Note;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities for working on multiple cards
 */
public class CardUtils {

    public static boolean hasUnmarked(List<Note> notes) {
        for (Note n : notes) {
            if (!n.hasTag("marked"))
                return true;
        }

        return false;
    }


    /**
     * @return List of corresponding notes without duplicates, even if the input list has multiple cards of the same note.
     */
    public static List<Note> getUniqueNotes(List<Card> cards) {
        Set<Long> noteIds = new HashSet<>();    // only used to check for duplicates
        List<Note> notes = new ArrayList<>();

        for (Card card : cards) {
            if (noteIds.add(card.getNid())) {
                Note n = card.note();
                notes.add(n);
            }
        }

        return notes;
    }

    /**
     * @return All cards of all notes (will contain duplicates if notes are not unique)
     */
    public static List<Card> getAllCards(List<Note> notes) {
        List<Card> allCards = new ArrayList<>();
        for (Note note : notes) {
            allCards.addAll(note.cards());
        }
        return allCards;
    }

    public static void markAll(List<Note> notes, boolean mark) {
        for (Note note : notes) {
            if (mark) {
                if (!note.hasTag("marked")) {
                    note.addTag("marked");
                    note.flush();
                }
            } else {
                note.delTag("marked");
                note.flush();
            }
        }
    }

    public static long[] unbox(Long[] array) {
        long[] primitive = new long[array.length];
        for (int i = 0; i < array.length; i++) {
            primitive[i] = array[i];
        }
        return primitive;
    }
}
