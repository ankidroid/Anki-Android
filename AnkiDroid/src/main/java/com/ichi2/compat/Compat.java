/****************************************************************************************
 * Copyright (c) 2011 Flavio Lerda <flerda@gmail.com>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.compat;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.widget.RemoteViews;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiActivity;

import java.io.File;

import io.requery.android.database.sqlite.SQLiteDatabase;

/**
 * This interface defines a set of functions that are not available on all platforms.
 * <p>
 * A set of implementations for the supported platforms are available.
 * <p>
 * Each implementation ends with a {@code V<n>} prefix, identifying the minimum API version on which this implementation
 * can be used. For example, see {@link CompatV15}.
 * <p>
 * Each implementation should extend the previous implementation and implement this interface.
 * <p>
 * Each implementation should only override the methods that first become available in its own version, use @Override.
 * <p>
 * Methods not supported by its API will default to the empty implementations of CompatV8.  Methods first supported
 * by lower APIs will default to those implementations since we extended them.
 * <p>
 * Example: CompatV21 extends CompatV19. This means that the setSelectableBackground function using APIs only available
 * in API 21, should be implemented properly in CompatV19 with @Override annotation. On the other hand a method
 * like showViewWithAnimation that first becomes available in API 19 need not be implemented again in CompatV21,
 * unless the behaviour is supposed to be different there.
 */
public interface Compat {

    /* Mock the Intent PROCESS_TEXT constants introduced in API 23. */
    String ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT";
    String EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT";

    String detagged(String txt);
    void disableDatabaseWriteAheadLogging(SQLiteDatabase db);
    void updateWidgetDimensions(Context context, RemoteViews updateViews, Class<?> cls);
    void setFullScreen(AbstractFlashcardViewer activity);
    void setSelectableBackground(View view);
    void openUrl(AnkiActivity activity, Uri uri);
    void prepareWebViewCookies(Context context);
    void flushWebViewCookies();
    void setHTML5MediaAutoPlay(WebSettings settings, Boolean allow);
    void setStatusBarColor(Window window, int color);

    /** Returns true if the system UI currently visible during immersive mode */
    boolean isImmersiveSystemUiVisible(AnkiActivity activity);
    boolean deleteDatabase(File db);
    Uri getExportUri(Context context, File file);
    void setupNotificationChannel(Context context, String id, String name);
}

