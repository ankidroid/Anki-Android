package com.ichi2.anki.tests;

import android.Manifest;
import android.app.Instrumentation;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import org.acra.ACRA;
import org.acra.builder.ReportBuilder;
import org.acra.collections.ImmutableList;
import org.acra.config.Configuration;
import org.acra.config.CoreConfiguration;
import org.acra.config.LimitingReportAdministrator;
import org.acra.config.ToastConfiguration;
import org.acra.data.CrashReportData;
import org.acra.data.CrashReportDataFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import timber.log.Timber;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class ACRATest {

    @Rule public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    private AnkiDroidApp app = null;

    private String[] debugLogcatArguments = { "-t", "300", "-v", "long", "ACRA:S"};
    //private String[] prodLogcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" };


    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        app = (AnkiDroidApp) instrumentation.getTargetContext().getApplicationContext();
        app.onCreate();
    }

    /**
     * helper method to invoke private method to set acra config builder
     *
     * @param mode either Debug or Production, used to construct method name to invoke
     * @param prefs the preferences to use during method invocation
     * @exception NoSuchFieldException if the method isn't found, possibly IllegalAccess or InvocationAccess as well
     */
    private void setAcraConfig(String mode, SharedPreferences prefs) throws Exception {
        Method method = app.getClass().getDeclaredMethod("set" + mode + "ACRAConfig", SharedPreferences.class);
        method.setAccessible(true);
        method.invoke(app, prefs);
    }

    @Test
    public void testDebugConfiguration() throws Exception {

        // Debug mode overrides all saved state so no setup needed
        setAcraConfig("Debug", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        assertArrayEquals("Debug logcat arguments not set correctly",
                app.getAcraCoreConfigBuilder().build().logcatArguments().toArray(),
                new ImmutableList<>(debugLogcatArguments).toArray());
        verifyDebugACRAPreferences();
    }

    private void verifyDebugACRAPreferences() {
        assertTrue("ACRA was not disabled correctly",
                AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext())
                        .getBoolean(ACRA.PREF_DISABLE_ACRA, true));
        assertEquals("ACRA feedback was not turned off correctly",
                AnkiDroidApp.FEEDBACK_REPORT_NEVER,
                AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext())
                        .getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "undefined"));
    }

    @Test
    public void testProductionConfigurationUserDisabled() throws Exception {

        // set up as if the user had prefs saved to disable completely
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_NEVER).commit();

        // ACRA initializes production logcat via annotation and we can't mock Build.DEBUG
        // That means we are restricted from verifying production logcat args and this is the debug case again
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        verifyDebugACRAPreferences();
    }

    @Test
    public void testProductionConfigurationUserAsk() throws Exception {
        // set up as if the user had prefs saved to ask
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ASK).commit();

        // If the user is set to ask, then it's production, with interaction mode dialog
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        verifyACRANotDisabled();

        CoreConfiguration config = app.getAcraCoreConfigBuilder().build();

        for (Configuration configuration : config.pluginConfigurations()) {

            // Make sure the toast is configured correctly
            if (configuration.getClass().toString().contains("Toast")) {
                assertEquals(app.getResources().getString(R.string.feedback_manual_toast_text),
                        ((ToastConfiguration)configuration).text());
                assertTrue("Toast is not enabled", configuration.enabled());
            }

            // Make sure the dialog is set to pop up
            if (configuration.getClass().toString().contains("Dialog")) {
                assertTrue("Dialog is not enabled", configuration.enabled());
            }
        }
    }

    @Test
    public void testCrashReportLimit() throws Exception {
        // To test ACRA switch on  reporting, plant a production tree, and trigger a report
        Timber.plant(new AnkiDroidApp.ProductionCrashReportingTree());

        // set up as if the user had prefs saved to full auto
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ALWAYS).commit();

        // If the user is set to always, then it's production, with interaction mode toast
        // will be useful with ACRA 5.2.0
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()));

        // The same class/method combo is only sent once, so we face a new method each time (should test that system later)
        Exception crash = new Exception("testCrashReportSend at " + System.currentTimeMillis());
        StackTraceElement[] trace = new StackTraceElement[] {
                new StackTraceElement("Class", "Method" + (int)System.currentTimeMillis(), "File", (int)System.currentTimeMillis())
        };
        crash.setStackTrace(trace);

        // one send should work
        CrashReportData crashData = new CrashReportDataFactory(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build()).createCrashData(new ReportBuilder().exception(crash));

        assertTrue(new LimitingReportAdministrator().shouldSendReport(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build(),
                crashData)
        );

        // A second send should not work
        assertFalse(new LimitingReportAdministrator().shouldSendReport(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build(),
                crashData)
        );

        // Now let's clear data
        AnkiDroidApp.deleteACRALimiterData(InstrumentationRegistry.getInstrumentation().getTargetContext());

        // A third send should work again
        assertTrue(new LimitingReportAdministrator().shouldSendReport(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build(),
                crashData)
        );
    }


    private void verifyACRANotDisabled() {
        assertFalse("ACRA was not enabled correctly",
                AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()).getBoolean(ACRA.PREF_DISABLE_ACRA, false));
    }


    @Test
    public void testProductionConfigurationUserAlways() throws Exception {
        // set up as if the user had prefs saved to full auto
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ALWAYS).commit();

        // If the user is set to always, then it's production, with interaction mode toast
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getInstrumentation().getTargetContext()));
        verifyACRANotDisabled();

        CoreConfiguration config = app.getAcraCoreConfigBuilder().build();
        for (Configuration configuration : config.pluginConfigurations()) {

            // Make sure the toast is configured correctly
            if (configuration.getClass().toString().contains("Toast")) {
                assertEquals(app.getResources().getString(R.string.feedback_auto_toast_text),
                        ((ToastConfiguration)configuration).text());
                assertTrue("Toast is not enabled", configuration.enabled());
            }

            // Make sure the dialog is disabled
            if (configuration.getClass().toString().contains("Dialog")) {
                assertFalse("Dialog is still enabled", configuration.enabled());
            }
        }
    }
}
