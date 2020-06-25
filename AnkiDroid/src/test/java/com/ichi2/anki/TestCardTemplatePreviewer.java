package com.ichi2.anki;

public class TestCardTemplatePreviewer extends CardTemplatePreviewer {
    protected boolean mShowingAnswer = false;
    public boolean getShowingAnswer() { return mShowingAnswer; }
    public void disableDoubleClickPrevention() { mLastClickTime = (AbstractFlashcardViewer.DOUBLE_TAP_IGNORE_THRESHOLD * -2); }


    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        mShowingAnswer = true;
    }


    @Override
    protected void displayCardQuestion() {
        super.displayCardQuestion();
        mShowingAnswer = false;
    }
}