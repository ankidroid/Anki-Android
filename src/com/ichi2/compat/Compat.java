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

import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.webkit.WebView;

/**
 * This interface defines a set of functions that are not available on all platforms.
 * <p>
 * A set of implementations for the supported platforms are available.
 * <p>
 * Each implementation ends with a {@code V<n>} prefix, identifying the minimum API version on which this implementation
 * can be used. For example, see {@link CompatV5}.
 * <p>
 * Each implementation should extend the previous implementation and implement this interface.
 * <p>
 * Each implementation should only override the methods that first become available in its own version, use @Override.
 * <p>
 * Methods not supported by its api, will default to the empty implementations of CompatV4.
 * Methods first supported by lower APIs, will default to those implementations since we extended them.
 * <p>
 * Example: CompatV9 extends CompatV5 as of the time of writing. This means that the normalizeUnicode method
 * that uses classes only available in API 9, should be implemented properly in CompatV9 with @Override annotatin.
 * On the other hand the method onAttachedToWindow that first becomes available in API 5 need not be implemented
 * again in CompatV9, unless the behaviour is supposed to be different there.
 */
public interface Compat {
    public abstract String normalizeUnicode(String txt);
    public abstract void setScrollbarFadingEnabled(WebView webview, boolean enable);
    public abstract void setOverScrollModeNever(View v);
    public abstract void invalidateOptionsMenu(Activity activity);
    public abstract void setActionBarBackground(Activity activity, int color);
    public abstract void setTitle(Activity activity, String title, boolean inverted);
    public abstract void setSubtitle(Activity activity, String title);
    public abstract void setSubtitle(Activity activity, String title, boolean inverted);
    public abstract void setTtsOnUtteranceProgressListener(TextToSpeech tts);
}
