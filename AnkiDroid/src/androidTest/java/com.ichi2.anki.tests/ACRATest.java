package com.ichi2.anki.tests;

import android.Manifest;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import org.acra.ACRA;
import org.acra.collections.ImmutableList;
import org.acra.config.Configuration;
import org.acra.config.CoreConfiguration;
import org.acra.config.ToastConfiguration;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.InstrumentationRegistry;

import java.lang.reflect.Method;

import timber.log.Timber;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
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
        setAcraConfig("Debug", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));
        assertArrayEquals("Debug logcat arguments not set correctly",
                app.getAcraCoreConfigBuilder().build().logcatArguments().toArray(),
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
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));

        // ACRA protects itself from re-.init() and with our BuildConfig.BUILD_DEBUG check
        // it is impossible to reinitialize as a production build until ACRA 5.2.0
        //verifyProductionLogcat();
        verifyDebugACRAPreferences();
    }

    //private void verifyProductionLogcat() throws Exception {
    //    assertArrayEquals("Production logcat arguments not set correctly",
    //            new ImmutableList<>(prodLogcatArguments).toArray(),
    //            app.getAcraCoreConfigBuilder().build().logcatArguments().toArray());
    //}

    @Test
    public void testProductionConfigurationUserAsk() throws Exception {
        // set up as if the user had prefs saved to ask
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ASK).commit();

        // If the user is set to ask, then it's production, with interaction mode dialog
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));
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
    public void testCrashReportSend() throws Exception {
        // To test ACRA switch on  reporting, plant a production tree, and trigger a report
        Timber.plant(new AnkiDroidApp.ProductionCrashReportingTree());

        // set up as if the user had prefs saved to full auto
        AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()).edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, AnkiDroidApp.FEEDBACK_REPORT_ALWAYS).commit();

        // If the user is set to always, then it's production, with interaction mode toast
        // will be useful with ACRA 5.2.0
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));

        // The same class/method combo is only sent once, so we face a new method each time (should test that system later)
        Exception crash = new Exception("testCrashReportSend at " + System.currentTimeMillis());
        StackTraceElement[] trace = new StackTraceElement[] {
                new StackTraceElement("Class", "Method" + (int)System.currentTimeMillis(), "File", (int)System.currentTimeMillis())
        };
        crash.setStackTrace(trace);

        // get the current hash, get the reports, make sure our hash is not in there
        Method getExceptionHash = app.getClass().getDeclaredMethod("getExceptionHash", Throwable.class);
        getExceptionHash.setAccessible(true);
        String exceptionHash = (String)getExceptionHash.invoke(app, crash);
        JSONObject sentReports = new JSONObject(AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext())
                .getString("sentExceptionReports", "{}"));
        assertFalse("Sent reports contains the exception already", sentReports.has(exceptionHash));

        // After ACRA 5.2.0 you should be able to go to the debug acralyzer and see the report
        AnkiDroidApp.sendExceptionReport(crash, "testing ACRA send");

        sentReports = new JSONObject(AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext())
                .getString("sentExceptionReports", "{}"));
        assertTrue("Sent reports should contain the exception", sentReports.has(exceptionHash));
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
        setAcraConfig("Production", AnkiDroidApp.getSharedPrefs(InstrumentationRegistry.getTargetContext()));
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
