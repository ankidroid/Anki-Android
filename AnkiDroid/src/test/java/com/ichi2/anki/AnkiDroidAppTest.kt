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


import com.ichi2.testutils.AnkiAssert;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.AnkiDroidApp.sendExceptionReport;

@RunWith(AndroidJUnit4.class)
public class AnkiDroidAppTest {

    @Test
    public void reportingDoesNotThrowException() {
        AnkiAssert.assertDoesNotThrow(() -> sendExceptionReport("Test", "AnkiDroidAppTest"));
    }

    @Test
    public void reportingWithNullMessageDoesNotFail() {
        String message = null;
        //It's meant to be non-null, but it's developer-defined, and we don't want a crash in the reporting dialog
        //noinspection ConstantConditions
        AnkiAssert.assertDoesNotThrow(() -> sendExceptionReport(message, "AnkiDroidAppTest"));
    }
}
