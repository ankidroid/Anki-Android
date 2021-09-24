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

package com.ichi2.anki;

import android.text.Spanned;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class) //required for String -> Spannable conversion in formatDescription
public class StudyOptionsFragmentTest {

    //Fixes for #5715: In deck description, ignore what is in style and script tag

    @Test
    public void spanTagsAreNotRemoved() {
        Spanned result = StudyOptionsFragment.formatDescription("a<span style=\"color:red\">a=1</span>a");
        assertEquals("aa=1a", result.toString()); //Note: This is coloured red on the screen
    }

    @Test
    public void scriptTagContentsAreRemoved() {
        Spanned result = StudyOptionsFragment.formatDescription("a<script>a=1</script>a");
        assertEquals("aa", result.toString());
    }

    @Test
    public void upperCaseScriptTagContentsAreRemoved() {
        Spanned result = StudyOptionsFragment.formatDescription("a<SCRIPT>a=1</script>a");
        assertEquals("aa", result.toString());
    }

    @Test
    public void scriptTagWithAttributesContentsAreRemoved() {
        Spanned result = StudyOptionsFragment.formatDescription("a<script type=\"application/javascript\">a=1</script>a");
        assertEquals("aa", result.toString());
    }

    @Test
    public void styleTagContentsAreRemoved() {
        Spanned result = StudyOptionsFragment.formatDescription("a<style>a=1</style>a");
        assertEquals("aa", result.toString());
    }

    @Test
    public void upperCaseStyleTagContentsAreRemoved() {
        Spanned result = StudyOptionsFragment.formatDescription("a<STYLE>a:1</style>a");
        assertEquals("aa", result.toString());
    }

    @Test
    public void styleTagWithAttributesContentsAreRemoved() {
        Spanned result = StudyOptionsFragment.formatDescription("a<style type=\"text/css\">a:1</style>a");
        assertEquals("aa", result.toString());
    }

    /** Begin #5188 - newlines weren't displayed */

    @Test //This was originally correct
    public void brIsDisplayedAsNewline() {
        Spanned result = StudyOptionsFragment.formatDescription("a<br/>a");
        assertEquals("a\na", result.toString());
    }

    @Test
    public void windowsNewlinesAreNewlines() {
        Spanned result = StudyOptionsFragment.formatDescription("a\r\na");
        assertEquals("a\na", result.toString());
    }

    @Test
    public void unixNewlinesAreNewlines() {
        Spanned result = StudyOptionsFragment.formatDescription("a\na");
        assertEquals("a\na", result.toString());
    }

    /* end #5188 */
}
