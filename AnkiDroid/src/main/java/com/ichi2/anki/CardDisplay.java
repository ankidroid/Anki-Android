package com.ichi2.anki;

import android.text.Spanned;
import android.text.SpannedString;

import com.ichi2.anki.reviewer.ReviewerExtRegistry;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Sound;
import com.ichi2.themes.HtmlColors;

import timber.log.Timber;

/**
 * Encapsulates a Card and renders the Question and Answer strings
 */
public class CardDisplay {

    public CardDisplay(Card card)
    {
        mCard = card;
    }

    public Card getCard() { return mCard; }

    /**
     * Render Question and Answer content
     */
    public void renderCard(Collection collection, boolean prefCenterVertically, ReviewerExtRegistry extensions, int cardZoom, int imageZoom, boolean nightMode, String cardTemplate, String baseUrl) {
        if( getCard() == null) {
            setQuestionContent(new SpannedString(""));
            setAnswerContent(new SpannedString(""));
            return;
        }

        // render question
        String question = getCard().q();
        question = collection.getMedia().escapeImages(question);
        String questionContent = AbstractFlashcardViewer.enrichWithQADiv(question, false);
        setQuestionContent(commonContentProcessing(questionContent, prefCenterVertically, extensions, cardZoom, imageZoom, nightMode, cardTemplate, baseUrl));
        // render answer
        String answer = getCard().a();
        answer = collection.getMedia().escapeImages(answer);
        String answerContent = AbstractFlashcardViewer.enrichWithQADiv(answer, true);
        setAnswerContent(commonContentProcessing(answerContent, prefCenterVertically, extensions, cardZoom, imageZoom, nightMode, cardTemplate, baseUrl));
    }

    private Spanned commonContentProcessing(String content, boolean prefCenterVertically, ReviewerExtRegistry extensions, int cardZoom, int imageZoom, boolean nightMode, String cardTemplate, String baseUrl)
    {

        content = Sound.expandSounds(baseUrl, content);

        // In order to display the bold style correctly, we have to change
        // font-weight to 700
        content = content.replace("font-weight:600;", "font-weight:700;");

        // CSS class for card-specific styling
        String cardClass = "card card" + (getCard().getOrd() + 1);

        if (prefCenterVertically) {
            cardClass += " vertically_centered";
        }

        Timber.d("content card = \n %s", content);
        StringBuilder style = new StringBuilder();
        extensions.updateCssStyle(style);

        // Zoom cards
        if (cardZoom != 100) {
            style.append(String.format("body { zoom: %s }\n", cardZoom / 100.0));
        }

        // Zoom images
        if (imageZoom != 100) {
            style.append(String.format("img { zoom: %s }\n", imageZoom / 100.0));
        }

        Timber.d("::style::", style);

        if (nightMode) {
            // Enable the night-mode class
            cardClass += " night_mode";
            // If card styling doesn't contain any mention of the night_mode class then do color inversion as fallback
            // TODO: find more robust solution that won't match unrelated classes like "night_mode_old"
            if (!getCard().css().contains(".night_mode")) {
                content = HtmlColors.invertColors(content);
            }
        }

        content = AbstractFlashcardViewer.SmpToHtmlEntity(content);
        return(new SpannedString(cardTemplate.replace("::content::", content)
                .replace("::style::", style.toString()).replace("::class::", cardClass)));
    }

    private void setQuestionContent(Spanned content){
        mCardQuestionContent = content;
    }

    private void setAnswerContent(Spanned content){
        mCardAnswerContent = content;
    }

    public Spanned getQuestionContent() { return mCardQuestionContent; }
    public Spanned getAnswerContent() { return mCardAnswerContent; }

    private Card mCard;
    private Spanned mCardQuestionContent;
    private Spanned mCardAnswerContent;

}
