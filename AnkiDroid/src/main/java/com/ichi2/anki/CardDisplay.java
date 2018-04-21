package com.ichi2.anki;

import android.text.Spanned;

import com.ichi2.libanki.Card;

public class CardDisplay {

    public CardDisplay(Card card)
    {
        mCard = card;
    }

    public Card getCard() { return mCard; }

    public void setContent(Spanned content) {
        mCardContent = content;
    }

    public Spanned getContent() { return mCardContent; }

    private Card mCard;
    private Spanned mCardContent;
}
