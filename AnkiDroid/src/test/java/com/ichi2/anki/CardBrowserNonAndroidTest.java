/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import org.junit.Test;

import androidx.annotation.CheckResult;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CardBrowserNonAndroidTest {

    @Test
    public void soundIsStrippedCorrectly() {
        String output = formatWithFilenamesStripped("aou[sound:foo.mp3]aou");

        assertThat(output, is("aou aou"));
    }

    @Test
    public void soundIsRetainedWithoutTag() {
        String output = formatWithFilenamesRetained("aou[sound:foo.mp3]aou");

        assertThat(output, is("aou foo.mp3 aou"));
    }

    @Test
    public void imageIsStrippedCorrectly() {
        String output = formatWithFilenamesStripped("aou<img src=\"test.jpg\">aou");

        assertThat(output, is("aou aou"));
    }

    @Test
    public void imageIsRetainedWithNoHtml() {
        String output = formatWithFilenamesRetained("aou<img src=\"test.jpg\">aou");

        assertThat(output, is("aou test.jpg aou"));
    }

    @CheckResult
    private String formatWithFilenamesRetained(String input) {
        return CardBrowser.formatQAInternal(input, true);
    }

    @CheckResult
    private String formatWithFilenamesStripped(String input) {
        return CardBrowser.formatQAInternal(input, false);
    }
}
