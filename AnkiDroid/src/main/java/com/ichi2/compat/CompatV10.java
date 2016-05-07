
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
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.Preferences;
import com.ichi2.anki.R;
import com.ichi2.anki.ReadText;
import com.ichi2.compat.customtabs.CustomTabsFallback;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 7 */
@TargetApi(10)
public class CompatV10 implements Compat {
    protected static final int FULLSCREEN_ALL_GONE = 2;

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

    public void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        // We've never used WAL mode on Gingerbread so don't need to do anything here
    }


    // Below API level 12, file scheme pages are not restricted, so no adjustment is needed.
    public void enableCookiesForFileSchemePages() {
        Timber.w("Cookies not supported in API version %d", CompatHelper.getSdkVersion());
    }

    // CookieSyncManager is need to be initialized before use.
    // Note: CookieSyncManager is deprecated since API level 21, but still need to be used here.
    public void prepareWebViewCookies(Context context) {
        CookieSyncManager.createInstance(context);
    }

    // A data of cookies may be lost when an application exists just after it was written.
    // Below API level 21, this problem can be solved by using CookieSyncManager.sync().
    // Note: CookieSyncManager is deprecated since API level 21, but still need to be used here.
    public void flushWebViewCookies() {
        CookieSyncManager.getInstance().sync();
    }

    // Below API level 17, there is no simple way to enable the auto play feature of HTML media elements.
    public void setHTML5MediaAutoPlay(WebSettings webSettings, Boolean allow) {

    }

    // Below API level 16, widget dimensions cannot be adjusted
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

    public void setFullScreen(AbstractFlashcardViewer a) {
        a.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final int fullscreenMode = Integer.parseInt(AnkiDroidApp.getSharedPrefs(a).getString("fullscreenMode", "0"));
        if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
            final LinearLayout answerButtons = (LinearLayout) a.findViewById(R.id.answer_options_layout);
            answerButtons.setVisibility(View.GONE);
        }
    }


    public void setSelectableBackground(View view) {
        // NOTE: we can't use android.R.attr.selectableItemBackground until API 11
        Context context = view.getContext();
        int[] attrs = new int[] {android.R.attr.colorBackground};
        TypedArray ta = context.obtainStyledAttributes(attrs);
        view.setBackgroundColor(ta.getColor(0, ContextCompat.getColor(context, R.color.white)));
        ta.recycle();
    }

    public void openUrl(AnkiActivity activity, Uri uri) {
        new CustomTabsFallback().openUri(activity, uri);
    }

    /**
     * FloatingActionsMenu has a bug on Android 2.3 where the collapsed menu items can still be clicked,
     * so we revert to showing a ContextMenu below API 14.
     * @param activity DeckPicker instance that we can run callbacks on
     */
    public void supportAddContentMenu(final DeckPicker activity) {
        Resources res = activity.getResources();
        new MaterialDialog.Builder(activity)
                .items(new String[]{res.getString(R.string.menu_add_note),
                        res.getString(R.string.menu_get_shared_decks),
                        res.getString(R.string.new_deck)})
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, View view, int i,
                                            CharSequence charSequence) {
                        switch (i) {
                            case 0:
                                activity.addNote();
                                break;
                            case 1:
                                activity.addSharedDeck();
                                break;
                            case 2:
                                final EditText mDialogEditText = new EditText(activity);
                                mDialogEditText.setSingleLine(true);
                                new MaterialDialog.Builder(activity)
                                        .title(R.string.new_deck)
                                        .positiveText(R.string.dialog_ok)
                                        .customView(mDialogEditText, true)
                                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                                            @Override
                                            public void onClick(@NonNull MaterialDialog d, @NonNull DialogAction w) {
                                                Timber.i("DeckPicker:: Creating new deck...");
                                                String deckName = mDialogEditText.getText().toString();
                                                activity.getCol().getDecks().id(deckName, true);
                                                activity.onRequireDeckListUpdate();
                                            }
                                        })
                                        .negativeText(R.string.dialog_cancel)
                                        .show();
                        }
                    }
                })
                .build().show();
    }

    @Override
    public Intent getPreferenceSubscreenIntent(Context context, String subscreen) {
        // We're using "legacy preference headers" below API 11
        Intent i = new Intent(context, Preferences.class);
        i.setAction(subscreen);
        return i;
    }

    @Override
    public void setStatusBarColor(Window window, int color) {
        // Not settable before API 21 so do nothing
    }

    @Override
    public boolean isImmersiveSystemUiVisible(AnkiActivity activity) {
        return false;   // Immersive mode introduced in KitKat
    }
}