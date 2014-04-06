package com.ichi2.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;

import com.ichi2.anki.R;

/** Implementation of {@link Compat} for SDK level 7 */
@TargetApi(7)
public class CompatV7 extends CompatV5 implements Compat {

    @Override
    public void setActionBarBackground(Activity activity, int color) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(new ColorDrawable(activity.getResources().getColor(color)));
        }
    }

    @Override
    public void setTitle(Activity activity, String title, boolean inverted) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(inverted ? R.color.white : R.color.black));
            SpannableStringBuilder ssb = new SpannableStringBuilder(title);
            ssb.setSpan(span, 0, ssb.length(), 0);
            actionBar.setTitle(ssb);
        }
    }

    @Override
    public void setSubtitle(Activity activity, String title) {
        setSubtitle(activity, title, false);
    }

    @Override
    public void setSubtitle(Activity activity, String title, boolean inverted) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            if (inverted) {
                CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(inverted ? R.color.white : R.color.black));
                SpannableStringBuilder ssb = new SpannableStringBuilder(title);
                ssb.setSpan(span, 0, ssb.length(), 0);
                actionBar.setSubtitle(ssb);
            } else {
                actionBar.setSubtitle(title);
            }
        }
    }

    @Override
    public void invalidateOptionsMenu(Activity activity) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        actionBarActivity.supportInvalidateOptionsMenu();
    }

}
