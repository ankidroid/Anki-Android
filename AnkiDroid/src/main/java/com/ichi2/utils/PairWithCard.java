package com.ichi2.utils;

import com.ichi2.libanki.Card;

public class PairWithCard<U> implements CardGetter {
    public final Card bool;
    public final U other;

    public Card getCard() {
        return bool;
    }

    public PairWithCard(Card bool, U other) {
        this.bool = bool;
        this.other = other;
    }
}
