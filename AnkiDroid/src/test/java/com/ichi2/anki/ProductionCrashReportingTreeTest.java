/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

import android.util.Log;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import timber.log.Timber;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class ProductionCrashReportingTreeTest {


    @Before
    public void setUp() {

        // setup - simply instrument the class and do same log init as production
        PowerMockito.mockStatic(Log.class);
        Timber.plant(new AnkiDroidApp.ProductionCrashReportingTree());
    }


    /**
     * The Production logger ignores verbose and debug logs on purpose
     * Make sure these ignored log levels are not passed to the platform logger
     */
    @Test
    public void testProductionDebugVerboseIgnored() {

        // set up the platform log so that if anyone calls these 2 methods at all, it throws
        Mockito.when(Log.v(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Verbose logging should have been ignored"));
        Mockito.when(Log.d(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Debug logging should be ignored"));

        // now call our wrapper - if it hits the platform logger it will throw
        try {
            Timber.v("verbose");
            Timber.d("debug");
        } catch (Exception e) {
            Assert.fail("we were unable to log without exception?");
        }

    }


    /**
     * The levels that are fully logged have special "tag" behavior per-level
     *
     * Info: always {@link AnkiDroidApp#TAG} as the logging tag
     * Warn/Error: tag is LoggingClass.className()'s most specific dot-separated String subsection
     */
    @Test
    @SuppressWarnings("PMD.JUnitTestsShoudIncludAssert")
    public void testProductionLogTag() {

        // setUp() instrumented the static, now exercise it
        Timber.i("info level message");
        Timber.w("warn level message");
        Timber.e("error level message");

        // verify that info level had the constant tag
        verifyStatic(Log.class, atLeast(1));
        Log.i(AnkiDroidApp.TAG, "info level message", null);

        // verify Warn/Error has final part of calling class name to start the message
        verifyStatic(Log.class, atLeast(1));
        Log.w(AnkiDroidApp.TAG, this.getClass().getSimpleName() + "/ " + "warn level message", null);
        verifyStatic(Log.class, atLeast(1));
        Log.e(AnkiDroidApp.TAG, this.getClass().getSimpleName() + "/ " + "error level message", null);
    }
}
