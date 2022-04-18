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

package com.ichi2.testutils;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;

public class AnkiAssertTest {

    // We're not going to use the class under test to verify that these are working.

    @Test
    public void assertThrowsNoException() {
        try {
            AnkiAssert.assertThrows(() -> { }, IllegalStateException.class);
            fail("No exception thrown");
        } catch (AssertionError e) {
            assertThat(e.getMessage(), containsString("Expected exception: IllegalStateException"));
            assertThat(e.getMessage(), containsString("No exception thrown"));
        }
    }

    @Test
    public void assertThrowsWrongException() {
        IllegalArgumentException toThrow = new IllegalArgumentException();
        try {
            AnkiAssert.assertThrows(() -> { throw toThrow; }, IllegalStateException.class);
            fail("No exception thrown");
        } catch (AssertionError e) {
            assertThat(e.getMessage(), containsString("Expected 'IllegalStateException' got 'IllegalArgumentException'"));
            assertThat(e.getCause(), notNullValue());
            assertThat(e.getCause(), sameInstance(toThrow));
        }
    }

    @Test
    public void assertThrowsSameException() {
        IllegalStateException ex = new IllegalStateException();

        IllegalStateException exception = AnkiAssert.assertThrows(() -> { throw ex; }, IllegalStateException.class);

        assertThat(exception, sameInstance(ex));
    }
}
