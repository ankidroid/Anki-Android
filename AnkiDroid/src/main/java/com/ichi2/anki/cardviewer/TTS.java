// This time, we're going to be doing the most that we can using ReadText.java. Rather than the reimplementation
// that happened with branch Cloze_TTS_#9590.
package com.ichi2.anki.cardviewer;

import android.content.Context;
import android.content.res.Resources;
import android.speech.tts.TextToSpeech;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.MetaDB;
import com.ichi2.anki.R;
import com.ichi2.anki.ReadText;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Sound;

import java.util.ArrayList;

import timber.log.Timber;

public class TTS {
    private ReadText mTTS;

    /**
     * @param card The card to check the type of before determining the ordinal.
     * @return The card ordinal. If it's a Cloze card, returns 0.
     */
    private int getOrdUsingCardType(Card card) {
        if (card.model().isCloze()) {
            return 0;
        } else {
            return card.getOrd();
        }
    }

    /**
     * Reads the text (using TTS) for the given side of a card.
     *
     * @param card     The card to play TTS for
     * @param cardSide The side of the current card to play TTS for
     */
    public void readCardText(Context context, final Card card, final Sound.SoundSide cardSide) {
        final String cardSideContent;
        if (Sound.SoundSide.QUESTION == cardSide) {
            cardSideContent = card.q(true);
        } else if (Sound.SoundSide.ANSWER == cardSide) {
            cardSideContent = card.getPureAnswer();
        } else {
            Timber.w("Unrecognised cardSide");
            return;
        }
        String clozeReplacement = context.getString(R.string.reviewer_tts_cloze_spoken_replacement);
        mTTS.readCardSide(cardSide, cardSideContent, AbstractFlashcardViewer.getDeckIdForCard(card), getOrdUsingCardType(card), clozeReplacement);
    }

    /**
     * Ask the user what language they want.
     *
     * @param card The card to read text from
     * @param qa   The card question or card answer
     */
    public void selectTts(Context context, Card card, Sound.SoundSide qa) {
        String mTextToRead = qa == Sound.SoundSide.QUESTION ? card.q(true) : card.getPureAnswer();
        // get the text from the card
        mTTS.selectTts(AbstractFlashcardViewer.getTextForTts(context, mTextToRead), AbstractFlashcardViewer.getDeckIdForCard(card), getOrdUsingCardType(card), qa);
    }
}
