package com.ichi2.compat;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import com.ichi2.anki.R;

/**
 * Implementation of {@link Compat} for SDK level 11
 */
@TargetApi(11)
public class CompatV11 extends CompatV9 implements Compat {
    @Override
    public void invalidateOptionsMenu(Activity activity) {
        activity.invalidateOptionsMenu();
    }

    @Override
    public void setActionBarBackground(Activity activity, int color) {
        ActionBar ab = activity.getActionBar();
        if (ab != null) {
            ab.setBackgroundDrawable(new ColorDrawable(activity.getResources().getColor(color)));
        }
    }

    @Override
    public void setTitle(Activity activity, String title, boolean inverted) {
        ActionBar ab = activity.getActionBar();
        if (ab != null) {
            CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(inverted ? R.color.white : R.color.black));
            SpannableStringBuilder ssb = new SpannableStringBuilder(title);
            ssb.setSpan(span, 0, ssb.length(), 0);
            ab.setTitle(ssb);
        }
    }

    @Override
    public void setSubtitle(Activity activity, String title) {
        setSubtitle(activity, title, false);
    }

    @Override
    public void setSubtitle(Activity activity, String title, boolean inverted) {
        ActionBar ab = activity.getActionBar();
        if (ab != null) {
            if (inverted) {
                CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(inverted ? R.color.white : R.color.black));
                SpannableStringBuilder ssb = new SpannableStringBuilder(title);
                ssb.setSpan(span, 0, ssb.length(), 0);
                ab.setSubtitle(ssb);
            } else {
                ab.setSubtitle(title);
            }
        }
    }
}
