package com.ichi2.anki;

import com.ichi2.anki.exception.DatabaseCorruptException;

public class TestCardTemplatePreviewer extends CardTemplatePreviewer {
    protected boolean mShowingAnswer = false;
    public boolean getShowingAnswer() { return mShowingAnswer; }
    public void disableDoubleClickPrevention() { mLastClickTime = (AbstractFlashcardViewer.DOUBLE_TAP_IGNORE_THRESHOLD * -2); }


    @Override
    protected void displayCardAnswer() throws DatabaseCorruptException {
        super.displayCardAnswer();
        mShowingAnswer = true;
    }


    @Override
    protected void displayCardQuestion() throws DatabaseCorruptException {
        super.displayCardQuestion();
        mShowingAnswer = false;
    }
}