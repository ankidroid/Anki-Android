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

package com.ichi2.anki.cardviewer;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TypedAnswerTest {

    @Test
    public void testMediaIsNotExpected() {
        //#0096 - Anki Desktop did not expect media.
        String input = "ya[sound:36_ya.mp3]<div><img src=\"paste-efbfdfbff329f818e3b5568e578234d0d0054067.png\" /><br /></div>";

        String expected = "ya";

        String actual = TypedAnswer.cleanCorrectAnswer(input);

        assertThat(actual, is(expected));
    }
}
