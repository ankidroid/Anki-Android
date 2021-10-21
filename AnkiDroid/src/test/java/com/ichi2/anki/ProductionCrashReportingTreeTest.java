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

import android.annotation.SuppressLint;
import android.util.Log;

import com.ichi2.testutils.AnkiAssert;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import timber.log.Timber;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@SuppressLint("LogNotTimber")
public class ProductionCrashReportingTreeTest {


    @Before
    public void setUp() {

        // setup - simply instrument the class and do same log init as production
        Timber.plant(new AnkiDroidApp.ProductionCrashReportingTree());
    }


    @After
    public void tearDown() {
        Timber.uprootAll();
    }


    /**
     * The Production logger ignores verbose and debug logs on purpose
     * Make sure these ignored log levels are not passed to the platform logger
     */
    @Test
    public void testProductionDebugVerboseIgnored() {

        try (MockedStatic<Log> ignored = mockStatic(Log.class)) {
            // set up the platform log so that if anyone calls these 2 methods at all, it throws
            when(Log.v(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Verbose logging should have been ignored"));
            when(Log.d(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Debug logging should be ignored"));
            when(Log.i(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("Info logging should throw!"));

            // now call our wrapper - if it hits the platform logger it will throw
            AnkiAssert.assertDoesNotThrow(() -> Timber.v("verbose"));
            AnkiAssert.assertDoesNotThrow(() -> Timber.d("debug"));

            try {
                Timber.i("info");
                Assert.fail("we should have gone to Log.i and thrown but did not? Testing mechanism failure.");
            } catch (Exception e) {
                // this means everything worked, we were counting on an exception
            }
        }
    }


    /**
     * The levels that are fully logged have special "tag" behavior per-level
     * <p>
     * Info: always {@link AnkiDroidApp#TAG} as the logging tag
     * Warn/Error: tag is LoggingClass.className()'s most specific dot-separated String subsection
     */
    @Test
    @SuppressWarnings("PMD.JUnitTestsShoudIncludAssert")
    public void testProductionLogTag() {

        try (MockedStatic<Log> autoClosed = mockStatic(Log.class)) {

            // Now let's run through our API calls...
            Timber.i("info level message");
            Timber.w("warn level message");
            Timber.e("error level message");

            // ...and make sure they hit the logger class post-processed correctly
            AnkiAssert.assertDoesNotThrow(() ->
                    autoClosed.verify(() -> Log.i(AnkiDroidApp.TAG, "info level message", null)));
            AnkiAssert.assertDoesNotThrow(() ->
                    autoClosed.verify(() -> Log.w(AnkiDroidApp.TAG, this.getClass().getSimpleName() + "/ " + "warn level message", null)));
            AnkiAssert.assertDoesNotThrow(() ->
                    autoClosed.verify(() -> Log.e(AnkiDroidApp.TAG, this.getClass().getSimpleName() + "/ " + "error level message", null)));
        }
    }
}
