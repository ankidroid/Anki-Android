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
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.widget.RemoteViews;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.DeckPicker;

/**
 * This interface defines a set of functions that are not available on all platforms.
 * <p>
 * A set of implementations for the supported platforms are available.
 * <p>
 * Each implementation ends with a {@code V<n>} prefix, identifying the minimum API version on which this implementation
 * can be used. For example, see {@link CompatV10}.
 * <p>
 * Each implementation should extend the previous implementation and implement this interface.
 * <p>
 * Each implementation should only override the methods that first become available in its own version, use @Override.
 * <p>
 * Methods not supported by its API will default to the empty implementations of CompatV8.  Methods first supported
 * by lower APIs will default to those implementations since we extended them.
 * <p>
 * Example: CompatV9 extends CompatV8. This means that the nfcNormalized function that uses classes only available
 * in API 9, should be implemented properly in CompatV9 with @Override annotation. On the other hand a method
 * like setOverScrollModeNever that first becomes available in API 9 need not be implemented again in CompatV10,
 * unless the behaviour is supposed to be different there.
 */
public interface Compat {
    String detagged(String txt);
    void setTtsOnUtteranceProgressListener(TextToSpeech tts);
    void disableDatabaseWriteAheadLogging(SQLiteDatabase db);
    void enableCookiesForFileSchemePages();
    void updateWidgetDimensions(Context context, RemoteViews updateViews, Class<?> cls);
    void restartActivityInvalidateBackstack(AnkiActivity activity);
    void setFullScreen(AbstractFlashcardViewer activity);
    void setSelectableBackground(View view);
    void openUrl(AnkiActivity activity, Uri uri);
    void supportAddContentMenu(final DeckPicker a);
    Intent getPreferenceSubscreenIntent(Context context, String subscreen);
    void prepareWebViewCookies(Context context);
    void flushWebViewCookies();
    void setHTML5MediaAutoPlay(WebSettings settings, Boolean allow);
    void setStatusBarColor(Window window, int color);
    /** Returns true if the system UI currently visible during immersive mode */
    boolean isImmersiveSystemUiVisible(AnkiActivity activity);
}

