package com.ichi2.utils;

import android.util.Pair;

import com.ichi2.anki.AbstractFlashcardViewer;

public class PairWithBoolean<Second> extends Pair<Boolean, Second>  {
    /**
     * Constructor for a Pair.
     *
     * @param first  the first object in the Pair
     * @param second the second object in the pair
     */
    public PairWithBoolean(Boolean first, Second second) {
        super(first, second);
    }

    public PairWithBoolean(Boolean first) {
        this(first, null);
    }


    public boolean getBoolean() {
        return first;
    }
}
