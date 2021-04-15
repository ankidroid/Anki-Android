/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.cardviewer;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class CardTemplateTest {
    @Mock
    private Context mMockContext;

    @Mock
    private SharedPreferences mMockSharedPreferences;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mMockContext.getSharedPreferences("mock_context_preferences", Context.MODE_PRIVATE))
                .thenReturn(mMockSharedPreferences);
    }

    @After
    public void validate() {
        validateMockitoUsage();
    }

    @Test
    @SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    public void testSendException() {
        try (MockedStatic<android.preference.PreferenceManager> ignored = mockStatic(android.preference.PreferenceManager.class)) {
            when(android.preference.PreferenceManager.getDefaultSharedPreferences(ArgumentMatchers.any()))
                    .thenReturn(mMockSharedPreferences);
        }
    }

    private static final String data = "<!doctype html>\n" +
            "<html class=\"mobile android linux js\">\n" +
            "    <head>\n" +
            "        <title>AnkiDroid Flashcard</title>\n" +
            "        <meta charset=\"utf-8\">\n" +
            "        <link rel=\"stylesheet\" type=\"text/css\" href=\"file:///android_asset/flashcard.css\">\n" +
            "        <link rel=\"stylesheet\" type=\"text/css\" href=\"file:///android_asset/chess.css\">\n" +
            "        <link rel=\"stylesheet\" type=\"text/css\" href=\"file:///android_asset/mathjax/mathjax.css\">\n" +
            "        <style>\n" +
            "        ::style::\n" +
            "        </style>\n" +
            "        <script src=\"file:///android_asset/mathjax/conf.js\"> </script>\n" +
            "        <script src=\"file:///android_asset/mathjax/MathJax.js\"> </script>\n" +
            "        <script src=\"file:///android_asset/scripts/card.js\" type=\"text/javascript\"> </script>\n" +
            "    </head>\n" +
            "    <body class=\"::class::\">\n" +
            "        <div id=\"content\">\n" +
            "        ::content::\n" +
            "        </div>\n" +
            "       <script>\n" +
            "       ::addons::\n" +
            "       </script>\n" +
            "    </body>\n" +
            "</html>\n";

    @Test
    public void replaceTest() {
        // Method is sped up - ensure that it still works.
        String content = "foo";
        String style = "bar";
        String cardClass = "baz";
        String addons = "addon";
        String result = new CardTemplate(data, mMockContext).render(content, style, cardClass);

        assertThat(result, is(data.replace("::content::", content).replace("::style::", style).replace("::class::", cardClass).replace("::addons::", addons)));
    }

    @Test
    public void stressTest() {
        // At length = 10000000
        // ~500ms before
        // < 200 after
        int stringLength = 1000;
        String content = new String(new char[stringLength]).replace('\0', 'a');


        String ret = new CardTemplate(data, mMockContext).render(content, content, content);
    }

}
