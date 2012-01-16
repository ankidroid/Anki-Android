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

package com.ichi2.anki;

import com.ichi2.libanki.Utils;

import android.test.AndroidTestCase;

/**
 * Unit tests for {@link Utils}.
 */
public class UtilsTest extends AndroidTestCase {

    public void testRemoveInvalidDeckNameCharacters() {
        // Null or empty string.
        assertEquals(null, Utils.removeInvalidDeckNameCharacters(null));
        assertEquals("", Utils.removeInvalidDeckNameCharacters(""));

        // Individual invalid characters are removed.
        assertEquals("", Utils.removeInvalidDeckNameCharacters(":"));
        assertEquals("", Utils.removeInvalidDeckNameCharacters("/"));
        assertEquals("", Utils.removeInvalidDeckNameCharacters("\\"));

        // Other characters are not removed.
        assertEquals("apple", Utils.removeInvalidDeckNameCharacters("apple"));
        assertEquals("apple,orange+peach", Utils.removeInvalidDeckNameCharacters("apple,orange+peach"));
        
        // Invalid characters are removed within the string.
        assertEquals("appleorangepeach", Utils.removeInvalidDeckNameCharacters("apple:orange/peach\\"));
    }

    
    public void testJoin_VarArgs() {
        assertEquals("", Utils.join(""));
        assertEquals("", Utils.join("+"));
        assertEquals("a", Utils.join("+", "a"));
        assertEquals("a+b", Utils.join("+", "a", "b"));
        assertEquals("a b", Utils.join(" ", "a", "b"));
        assertEquals("a, b", Utils.join(", ", "a", "b"));
        assertEquals("a, b, a, c", Utils.join(", ", "a", "b", "a", "c"));
    }


    public void testJoin_Array() {
        assertEquals("", Utils.join("", new String[]{}));
        assertEquals("", Utils.join("+", new String[]{}));
        assertEquals("a", Utils.join("+", new String[]{ "a" }));
        assertEquals("a+b", Utils.join("+", new String[]{ "a", "b" }));
        assertEquals("a b", Utils.join(" ", new String[]{ "a", "b" }));
        assertEquals("a, b", Utils.join(", ", new String[]{ "a", "b" }));
        assertEquals("a, b, a, c", Utils.join(", ", new String[]{ "a", "b", "a", "c" }));
    }

}
