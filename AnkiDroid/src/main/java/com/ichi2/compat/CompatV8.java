
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.RemoteViews;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.ReadText;
import com.ichi2.anki.exception.APIVersionException;

import java.util.regex.Pattern;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 7 */
@TargetApi(8)
public class CompatV8 implements Compat {

    // Only match text that is entirely ASCII.
    private static final Pattern fASCII = Pattern.compile("^\\p{ASCII}*$");

    /*
     *  Return the input string. Not doing the NFC normalization may cause a typed answer to be displayed as
     *  incorrect when it was correct, but stored in a different but equivalent form in the card. (E.g. umlauts
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
        if (txt == null) {
            return "";
        }
        return txt.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace(
                "'", "&#39;");
    }


    public void setOverScrollModeNever(View v) {
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


    // Below API level 12, file scheme pages are not restricted, so no adjustment is needed.
    public void enableCookiesForFileSchemePages() {
        Timber.w("Cookies not supported in API version %d", CompatHelper.getSdkVersion());
    }


    // Below API level 16, widget dimensions cannot be adjusted
    @Override
    public void updateWidgetDimensions(Context context, RemoteViews updateViews, Class<?> cls) {

    }

    public void setAlpha(View view, float alpha) {
        AlphaAnimation alphaAnimation = new AlphaAnimation(alpha, alpha);
        alphaAnimation.setDuration(0); // Make animation instant
        alphaAnimation.setFillAfter(true); // Tell it to persist after the animation ends
        view.startAnimation(alphaAnimation);
    }

    /**
     * Pre-honeycomb just completely boot back to the DeckPicker
     */
    public void restartActivityInvalidateBackstack(AnkiActivity activity) {
        Timber.i("AnkiActivity -- restartActivityInvalidateBackstack()");
        //TODO: Find a way to recreate the backstack even pre-Honeycomb
        Intent intent = new Intent(activity, DeckPicker.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivityWithoutAnimation(intent);
    }

    @Override
    public void setFullScreen(NavigationDrawerActivity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
}
