package com.ichi2.anki;

import android.text.Spanned;

import com.ichi2.libanki.Card;

public class CardDisplay {

    public CardDisplay(Card card, boolean isCurrentCard)
    {
        mCard = card;
        mIsCurrentCard = isCurrentCard;
    }

    public Card getCard() { return mCard; }

    public boolean isCurrentCard() { return mIsCurrentCard; }

    public void setContent(Spanned content) {
        mCardContent = content;
    }

    public Spanned getContent() { return mCardContent; }

    private Card mCard;
    private Spanned mCardContent;

    private boolean mIsCurrentCard; // true for current card, false for following
}
