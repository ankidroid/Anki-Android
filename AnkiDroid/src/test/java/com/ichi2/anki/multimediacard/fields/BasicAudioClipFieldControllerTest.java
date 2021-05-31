/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.multimediacard.fields;

import org.junit.Test;

import static com.ichi2.anki.multimediacard.fields.BasicAudioClipFieldController.checkFileName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BasicAudioClipFieldControllerTest {


    @Test
    public void test_bad_chars_stripped() {
        assertThat(checkFileName("There's a good film on the day <b>after</b> tomorrow.mp3"), is("There_s_a_good_film_on_the_day_b_after_b_tomorrow.mp3"));
    }

    @Test
    public void test_extension_is_not_stripped() {
        assertThat(checkFileName("file_example_MP3_700KB.mp3"), is("file_example_MP3_700KB.mp3"));
    }

}
