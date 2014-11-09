
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;

import com.ichi2.anki.R;
import com.ichi2.anki.ReadText;
import com.ichi2.anki.exception.APIVersionException;

import java.util.regex.Pattern;

/** Implementation of {@link Compat} for SDK level 7 */
@TargetApi(7)
public class CompatV7 implements Compat {


    // Only match text that is entirely ASCII.
    private static final Pattern fASCII = Pattern.compile("^\\p{ASCII}*$");
    
    /*
     *  Return the input string. Not doing the NFD normalization may cause problems with syncing media to Macs
     *  where the file name contains diacritics, as file names are decomposed on HFS file systems.
     *
     * @param txt Text to be normalized
     * @return The input text, not NFD normalized.
    */
    public String nfdNormalized(String txt) {
        return txt;
    }


    /*
     *  Return the input string. Not doing the NFC normalization may cause a typed answer to be displayed as
     *  incorrect when it was correct, but stored in a differnt but equivalent form in the card. (E.g. umlauts
     *  stored decomposed but typed as single characters.)
     *
     * @param txt Text to be normalized
     * @return The input text not NFC normalized.
    */
    @Override
    public String nfcNormalized(String txt) throws APIVersionException {
        // We will at least try to check if the string can be represented entirely in ASCII.
        // If it can be, we can be sure it's normalized. If not, we throw since we can't
        // make a guarantee about the actual normalized state of the string.
        if (fASCII.matcher(txt).find()) {
            return txt;
        } else {
            throw new APIVersionException("NFC normalization is not available in this API version");
        }
    }


    /*
     *  Return the input string in a form suitable for display on a HTML page. Replace “<”, “>”, “&”, “"” and “'” with
     *  HTML entities.
     *
     * @param txt Text to be cleaned.
     * @return The input text, with HTML tags and entities escaped.
    */
    public String detagged(String txt) {
        if (txt == null)
        {
            return "";
        }
        return txt.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace(
                "'", "&#39;");
    }


    public void setScrollbarFadingEnabled(WebView webview, boolean enable) {
        webview.setScrollbarFadingEnabled(enable);
    }


    public void setOverScrollModeNever(View v) {
    }


    public void invalidateOptionsMenu(Activity activity) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        actionBarActivity.supportInvalidateOptionsMenu();
    }


    public void setActionBarBackground(Activity activity, int color) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(new ColorDrawable(activity.getResources().getColor(color)));
        }
    }


    public void setTitle(Activity activity, String title, boolean inverted) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(
                    inverted ? R.color.white : R.color.black));
            SpannableStringBuilder ssb = new SpannableStringBuilder(title);
            ssb.setSpan(span, 0, ssb.length(), 0);
            actionBar.setTitle(ssb);
        }
    }


    public void setSubtitle(Activity activity, String title) {
        setSubtitle(activity, title, false);
    }


    public void setSubtitle(Activity activity, String title, boolean inverted) {
        ActionBarActivity actionBarActivity = (ActionBarActivity) activity;
        ActionBar actionBar = actionBarActivity.getSupportActionBar();
        if (actionBar != null) {
            if (inverted) {
                CharacterStyle span = new ForegroundColorSpan(activity.getResources().getColor(
                        inverted ? R.color.white : R.color.black));
                SpannableStringBuilder ssb = new SpannableStringBuilder(title);
                ssb.setSpan(span, 0, ssb.length(), 0);
                actionBar.setSubtitle(ssb);
            } else {
                actionBar.setSubtitle(title);
            }
        }
    }


    public void setTtsOnUtteranceProgressListener(TextToSpeech tts) {
        tts.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
            @Override
            public void onUtteranceCompleted(String utteranceId) {
                if (ReadText.sTextQueue.size() > 0) {
                    String[] text = ReadText.sTextQueue.remove(0);
                    ReadText.speak(text[0], text[1]);
                }
            }
        });
    }


    public void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
    }


    public void requestAudioFocus(AudioManager audioManager) {
    }


    public void abandonAudioFocus(AudioManager audioManager) {
    }

    public int parentLayoutSize() {
        return LayoutParams.FILL_PARENT;
    }
}
