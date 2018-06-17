package com.ichi2.anki;

import android.util.Log;

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
        Mockito.when(Log.v(anyString(), anyString(), (Throwable) any()))
                .thenThrow(new RuntimeException("Verbose logging should have been ignored"));
        Mockito.when(Log.d(anyString(), anyString(), (Throwable) any()))
                .thenThrow(new RuntimeException("Debug logging should be ignored"));

        // now call our wrapper - if it hits the platform logger it will throw
        Timber.v("verbose");
        Timber.d("debug");
    }


    /**
     * The levels that are fully logged have special "tag" behavior per-level
     *
     * Info: always {@link AnkiDroidApp#TAG} as the logging tag
     * Warn/Error: tag is LoggingClass.className()'s most specific dot-separated String subsection
     */
    @Test
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
