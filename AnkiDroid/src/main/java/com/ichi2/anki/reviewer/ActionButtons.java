package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.view.Menu;

public class ActionButtons
{
    private ActionButtonStatus mActionButtonStatus;

    public ActionButtons(ReviewerUi reviewerUi) {
        this.mActionButtonStatus = new ActionButtonStatus(reviewerUi);
    }

    public void setup(SharedPreferences preferences) {
        this.mActionButtonStatus.setup(preferences);
    }

    public void setCustomButtons(Menu menu) {
        this.mActionButtonStatus.setCustomButtons(menu);
    }


    public ActionButtonStatus getStatus() {
        //DEFECT: This should be private - it breaks the law of demeter, but it'll be a large refactoring to get
        // to this point
        return this.mActionButtonStatus;
    }
}
