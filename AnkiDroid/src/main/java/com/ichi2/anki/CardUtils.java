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

    /**
     * @return List of corresponding notes without duplicates, even if the input list has multiple cards of the same note.
     */
    public static Set<Note> getNotes(List<Card> cards) {
        Set<Note> notes = new HashSet<>();

        for (Card card : cards) {
            notes.add(card.note());
        }

        return notes;
    }

    /**
     * @return All cards of all notes
     */
    public static List<Card> getAllCards(Set<Note> notes) {
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
