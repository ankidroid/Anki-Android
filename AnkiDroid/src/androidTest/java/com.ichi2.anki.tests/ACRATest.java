package com.ichi2.anki.tests;
;
import android.Manifest;
import android.app.Instrumentation;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.collections.ImmutableList;
import org.acra.config.ACRAConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.InstrumentationRegistry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class ACRATest {

    @Rule public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    private AnkiDroidApp app = null;

    private String[] debugLogcatArguments = { "-t", "300", "-v", "long", "ACRA:S"};
    private String[] prodLogcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" };


    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        app = (AnkiDroidApp) instrumentation.getTargetContext().getApplicationContext();
    }

    @Test
    public void testDebugConfiguration() throws Exception {

        // Debug mode overrides all saved state so no setup needed
        app.setDebugACRAConfig(AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));
        assertArrayEquals("Debug logcat arguments not set correctly",
                app.getAcraConfigBuilder().build().logcatArguments().toArray(),
                new ImmutableList<>(debugLogcatArguments).toArray());
        verifyDebugACRAPreferences();
    }

    private void verifyDebugACRAPreferences() {
        assertEquals("ACRA was not disabled correctly",
                true,
                AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext())
                        .getBoolean(ACRA.PREF_DISABLE_ACRA, true));
        assertEquals("ACRA feedback was not turned off correctly",
                AnkiDroidApp.FEEDBACK_REPORT_NEVER,
                AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext())
                        .getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "undefined"));
    }

    @Test
    public void testProductionConfigurationUserDisabled() throws Exception {

        // set up as if the user had prefs saved to disable completely
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_NEVER).commit();

        // If the user disabled it, then it's the debug case except the logcat args
        app.setProductionACRAConfig(AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));

        // ACRA protects itself from re-.init() and with our BuildConfig.BUILD_DEBUG check
        // it is impossible to reinitialize as a production build, so we can't verify this
        // right now
        //verifyProductionLogcat();
        verifyDebugACRAPreferences();
    }

    private void verifyProductionLogcat() throws Exception {
        assertArrayEquals("Production logcat arguments not set correctly",
                new ImmutableList<>(prodLogcatArguments).toArray(),
                app.getAcraConfigBuilder().build().logcatArguments().toArray());
    }

    @Test
    public void testProductionConfigurationUserAsk() throws Exception {
        // set up as if the user had prefs saved to ask
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ASK).commit();

        // If the user is set to ask, then it's production, with interaction mode dialog
        app.setProductionACRAConfig(AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));
        verifyACRANotDisabled();

        ACRAConfiguration config = app.getAcraConfigBuilder().build();
        assertEquals(ReportingInteractionMode.DIALOG, config.reportingInteractionMode());
        assertEquals(R.string.feedback_manual_toast_text, config.resToastText());
    }


    private void verifyACRANotDisabled() {
        assertEquals("ACRA was not enabled correctly",
                false,
                AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()).getBoolean(ACRA.PREF_DISABLE_ACRA, false));
    }

    @Test
    public void testProductionConfigurationUserAlways() throws Exception {
        // set up as if the user had prefs saved to full auto
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ALWAYS).commit();

        // If the user is set to always, then it's production, with interaction mode toast
        app.setProductionACRAConfig(AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));
        verifyACRANotDisabled();

        ACRAConfiguration config = app.getAcraConfigBuilder().build();
        assertEquals(ReportingInteractionMode.TOAST, config.reportingInteractionMode());
        assertEquals(R.string.feedback_auto_toast_text, config.resToastText());
    }
}
