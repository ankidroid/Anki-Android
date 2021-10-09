//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki;

public class TestCardTemplatePreviewer extends CardTemplatePreviewer {
    protected boolean mShowingAnswer = false;
    public boolean getShowingAnswer() { return mShowingAnswer; }
    public void disableDoubleClickPrevention() {
        mLastClickTime = (AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL) * -2);
    }


    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        mShowingAnswer = true;
    }


    @Override
    public void displayCardQuestion() {
        super.displayCardQuestion();
        mShowingAnswer = false;
    }
}