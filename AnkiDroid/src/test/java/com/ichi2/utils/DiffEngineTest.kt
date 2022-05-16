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

package com.ichi2.utils;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.testutils.EmptyApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
@Config(application = EmptyApplication.class)
public class DiffEngineTest extends RobolectricTest {

    @Test
    public void checkEscapedHtmlCharacters() {
        // The HTML escaping that used to occur in 13c27a6a1fa8465cc6656c67bd9db25afc7a51fa (CompatV15.detagged)
        // This was the original intention of the escaping.
        String input = "<>& \\ aa";

        String output = DiffEngine.wrapMissing(input);

        assertThat(output, containsString("&lt;&gt;&amp; &#x5c; aa"));
    }

    @Test
    public void quoteEscaping() {
        // This is an interesting one - escaping a bare quote is not necessary but escaping a quote in an attribute is
        // A breakage here may be acceptable, but should flag the issue for investigation.
        String input = "\"'";

        String output = DiffEngine.wrapMissing(input);

        assertThat(output, containsString("&quot;&#39;"));
    }

    @Test
    public void polytonicGreekIsNotEscaped() {
        // Implies too much escaping is being performed, which will degrade performance
        // Not required for #7896 - this behaviour could be reversed without negative impact.
        String input = "αὐτός";

        String output = DiffEngine.wrapMissing(input);

        assertThat("There were problems displaying 'ὐ' when output as &#8016;", output, containsString(input));
    }
}
