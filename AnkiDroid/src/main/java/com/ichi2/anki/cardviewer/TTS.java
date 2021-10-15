/*
 *  Copyright (c) 2021 Tyler Lewis <tyler.r.lewis1@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// This time, we're going to be doing the most that we can using ReadText.java. Rather than the reimplementation
// that happened with branch Cloze_TTS_#9590.
package com.ichi2.anki.cardviewer;

import android.content.Context;
import android.content.res.Resources;
import android.speech.tts.TextToSpeech;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.CardUtils;
import com.ichi2.anki.MetaDB;
import com.ichi2.anki.R;
import com.ichi2.anki.ReadText;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Sound;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.template.TemplateFilters;

import java.util.ArrayList;

import timber.log.Timber;

public class TTS {
    private ReadText mTTS;

    /**
     * Returns the card ordinal for TTS language storage.
     *
     * The ordinal of a Cloze card denotes the cloze deletion, causing the TTS
     * language to be requested and stored on every new highest cloze deletion when
     * used normally.
     *
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
        mTTS.readCardSide(cardSide, cardSideContent, CardUtils.getDeckIdForCard(card), getOrdUsingCardType(card), clozeReplacement);
    }

    /**
     * Ask the user what language they want.
     *
     * @param card The card to read text from
     * @param qa   The card question or card answer
     */
    public void selectTts(Context context, Card card, Sound.SoundSide qa) {
        String textToRead = qa == Sound.SoundSide.QUESTION ? card.q(true) : card.getPureAnswer();
        // get the text from the card
        mTTS.selectTts(getTextForTts(context, textToRead), CardUtils.getDeckIdForCard(card), getOrdUsingCardType(card), qa);
    }

    public String getTextForTts(Context context, String text) {
        String clozeReplacement = context.getString(R.string.reviewer_tts_cloze_spoken_replacement);
        String clozeReplaced = text.replace(TemplateFilters.CLOZE_DELETION_REPLACEMENT, clozeReplacement);
        return Utils.stripHTML(clozeReplaced);
    }
}
