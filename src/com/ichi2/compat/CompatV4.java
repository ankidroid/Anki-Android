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

import com.ichi2.anki.ReadText;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.webkit.WebView;

/**
 * Implementation of {@link Compat} for SDK level 4.
 * This should contain the implementations for non-supported by low SDKs methods.
 */
public class CompatV4 implements Compat {
    public String normalizeUnicode(String txt) {
        return txt;
    }
    public void setScrollbarFadingEnabled(WebView webview, boolean enable) { }
    public void setOverScrollModeNever(View v) { }
    public void invalidateOptionsMenu(Activity activity) { }
	public void setActionBarBackground(Activity activity, int color) { }
	public void setTitle(Activity activity, String title, boolean inverted) {
		activity.setTitle(title);
	}
	public void setSubtitle(Activity activity, String title) { }
	public void setSubtitle(Activity activity, String title, boolean inverted) { }
	@Override
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

}
