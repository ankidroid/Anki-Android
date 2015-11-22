
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.NavigationDrawerActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.ReadText;
import com.ichi2.compat.customtabs.CustomTabsFallback;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 7 */
@TargetApi(10)
public class CompatV10 implements Compat {
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

    public boolean isWriteAheadLoggingEnabled(SQLiteDatabase db) {
        // don't use WAL mode on Gingerbread
        return false;
    }

    public void enableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        // don't use WAL mode on Gingerbread
    }

    public void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        // don't use WAL mode on Gingerbread
    }


    // Below API level 12, file scheme pages are not restricted, so no adjustment is needed.
    public void enableCookiesForFileSchemePages() {
        Timber.w("Cookies not supported in API version %d", CompatHelper.getSdkVersion());
    }


    // Below API level 16, widget dimensions cannot be adjusted
    @Override
    public void updateWidgetDimensions(Context context, RemoteViews updateViews, Class<?> cls) {

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
    public void setFullScreen(AbstractFlashcardViewer activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }


    @Override
    public void setSelectableBackground(View view) {
        // NOTE: we can't use android.R.attr.selectableItemBackground until API 11
        Resources res = view.getContext().getResources();
        int[] attrs = new int[] {android.R.attr.colorBackground};
        TypedArray ta = view.getContext().obtainStyledAttributes(attrs);
        view.setBackgroundColor(ta.getColor(0, res.getColor(R.color.white)));
        ta.recycle();
    }

    @Override
    public void openUrl(AnkiActivity activity, Uri uri) {
        new CustomTabsFallback().openUri(activity, uri);
    }
}