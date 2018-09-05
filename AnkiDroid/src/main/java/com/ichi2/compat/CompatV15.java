package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.compat.customtabs.CustomTabsFallback;

import java.io.File;

import io.requery.android.database.sqlite.SQLiteDatabase;

/** Implementation of {@link Compat} for SDK level 15 */
@TargetApi(15)
public class CompatV15 implements Compat {

    // Before API 16, we don't have Html.escapeHtml available
    @Override
    public String detagged(String txt) {
        if (txt == null) {
            return "";
        }
        return txt.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace(
                "'", "&#39;");
    }

    // disableWriteAheadLogging() method only available from API 16
    @Override
    public void disableDatabaseWriteAheadLogging(SQLiteDatabase db) {
        db.rawQuery("PRAGMA journal_mode = DELETE", null);
    }

    // CookieSyncManager needs to be initialized before use.
    // Note: CookieSyncManager is deprecated since API level 21, but we still need to use it here
    @Override
    @SuppressWarnings("deprecation")
    public void prepareWebViewCookies(Context context) {
        CookieSyncManager.createInstance(context);
    }

    // Cookie data may be lost when an application exists just after it was written.
    // Below API level 21, this problem can be solved by using CookieSyncManager.sync().
    // Note: CookieSyncManager.sync() is deprecated since API level 21, but still needed here
    @Override
    @SuppressWarnings("deprecation")
    public void flushWebViewCookies() {
        CookieSyncManager.getInstance().sync();
    }

    // Below API level 17, there is no simple way to enable the auto play feature of HTML media elements.
    @Override
    public void setHTML5MediaAutoPlay(WebSettings webSettings, Boolean allow) {}

    // Below API level 16, widget dimensions cannot be adjusted
    @Override
    public void updateWidgetDimensions(Context context, RemoteViews updateViews, Class<?> cls) {}

    // Immersive full screen isn't ready until API 19
    static final int FULLSCREEN_ALL_GONE = 2;
    @Override
    public void setFullScreen(AbstractFlashcardViewer a) {
        a.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        final int fullscreenMode = Integer.parseInt(AnkiDroidApp.getSharedPrefs(a).getString("fullscreenMode", "0"));
        if (fullscreenMode >= FULLSCREEN_ALL_GONE) {
            final LinearLayout answerButtons = a.findViewById(R.id.answer_options_layout);
            answerButtons.setVisibility(View.GONE);
        }
    }

    // NOTE: we can't use android.R.attr.selectableItemBackground until API 21
    @Override
    public void setSelectableBackground(View view) {
        Context context = view.getContext();
        int[] attrs = new int[] {android.R.attr.colorBackground};
        TypedArray ta = context.obtainStyledAttributes(attrs);
        view.setBackgroundColor(ta.getColor(0, ContextCompat.getColor(context, R.color.white)));
        ta.recycle();
    }

    // CustomTabs aren't available until API >= 16
    @Override
    public void openUrl(AnkiActivity activity, Uri uri) {
        new CustomTabsFallback().openUri(activity, uri);
    }

    // Not settable before API 21 so do nothing
    @Override
    public void setStatusBarColor(Window window, int color) {}

    // Immersive mode introduced in API 19
    @Override
    public boolean isImmersiveSystemUiVisible(AnkiActivity activity) { return false; }

    // Directly delete the file until API 16
    @Override
    public boolean deleteDatabase(File db) { return db.delete(); }

    // Until API 16, we can't use FileProvider
    @Override
    public Uri getExportUri(Context context, File file) {
        return Uri.fromFile(file);
    }

    @Override
    public void setupNotificationChannel(Context context, String id, String name) { /* pre-API26, do nothing */ }
}
