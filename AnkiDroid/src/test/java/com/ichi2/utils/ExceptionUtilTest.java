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

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ExceptionUtilTest {

    @Test
    public void exceptionMessageSingle() {
        Exception e = new Exception("Hello");

        String message = ExceptionUtil.getExceptionMessage(e);

        assertThat(message, is("Hello"));
    }

    @Test
    public void exceptionMessageNested() {
        Exception inner = new Exception("Inner");
        Exception e = new Exception("Hello", inner);

        String message = ExceptionUtil.getExceptionMessage(e);

        assertThat(message, is("Hello\nInner"));
    }

    @Test
    public void exceptionMessageNull() {
        String message = ExceptionUtil.getExceptionMessage(null);

        assertThat(message, is(""));
    }

    @Test
    public void exceptionMessageNestedNull() {
        // a single null should be displayed, a nested null shouldn't be
        Exception inner = new Exception();
        Exception e = new Exception("Hello", inner);

        String message = ExceptionUtil.getExceptionMessage(e);

        assertThat(message, is("Hello"));
    }

    @Test
    public void containsCauseExact() {
        Exception ex = new IllegalStateException();
        assertThat(ExceptionUtil.containsCause(ex, IllegalStateException.class), is(true));
    }

    @Test
    public void containsCauseNested() {
        Exception ex = new Exception(new IllegalStateException());
        assertThat(ExceptionUtil.containsCause(ex, IllegalStateException.class), is(true));
    }

    @Test
    public void containsCauseMissing() {
        Exception ex = new Exception();
        assertThat(ExceptionUtil.containsCause(ex, IllegalStateException.class), is(false));
    }

    @Test
    public void containsCauseMissingNested() {
        Exception ex = new Exception(new Exception());
        assertThat(ExceptionUtil.containsCause(ex, IllegalStateException.class), is(false));
    }
}
