package com.ichi2.anki;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Note;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities for working on multiple cards
 */
public class CardUtils {

    /**
     * @return List of corresponding notes without duplicates, even if the input list has multiple cards of the same note.
     */
    public static Set<Note> getNotes(Collection<Card> cards) {
        Set<Note> notes = new HashSet<>(cards.size());

        for (Card card : cards) {
            notes.add(card.note());
        }

        return notes;
    }

    /**
     * @return All cards of all notes
     */
    public static List<Card> getAllCards(Set<Note> notes) {
        List<Card> allCards = new ArrayList<>(notes.size());
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

    public static boolean isIn(long[] array, long val) {
        for (long v : array) {
            if (v == val) {
                return true;
            }
        }
        return false;
    }
}
