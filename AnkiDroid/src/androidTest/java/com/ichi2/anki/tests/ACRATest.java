package com.ichi2.anki.tests;

import android.Manifest;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;
import androidx.test.annotation.UiThreadTest;
import androidx.test.rule.GrantPermissionRule;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import org.acra.ACRA;
import org.acra.builder.ReportBuilder;
import org.acra.collections.ImmutableList;
import org.acra.config.ACRAConfigurationException;
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

import static com.ichi2.anki.AnkiDroidApp.FEEDBACK_REPORT_ALWAYS;
import static com.ichi2.anki.AnkiDroidApp.FEEDBACK_REPORT_ASK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class ACRATest extends InstrumentedTest {

    @Rule public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    private AnkiDroidApp mApp = null;

    private final String[] debugLogcatArguments = { "-t", "300", "-v", "long", "ACRA:S"};
    //private String[] prodLogcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" };


    @Before
    @UiThreadTest
    public void setUp() {
        mApp = (AnkiDroidApp) getTestContext().getApplicationContext();
        // Note: attachBaseContext can't be called twice as we're using the same instance between all tests.
        mApp.onCreate();
    }

    /**
     * helper method to invoke private method to set acra config builder
     *
     * @param mode either Debug or Production, used to construct method name to invoke
     * @param prefs the preferences to use during method invocation
     * @exception NoSuchFieldException if the method isn't found, possibly IllegalAccess or InvocationAccess as well
     */
    private void setAcraConfig(String mode, SharedPreferences prefs) throws Exception {
        Method method = findSetAcraConfigMethod(mode);
        method.setAccessible(true);
        method.invoke(mApp, prefs);
    }

    /** @return the method: "set[Debug/Production]ACRAConfig" on the application instance */
    private Method findSetAcraConfigMethod(String mode) {
        Class<?> clazz = mApp.getClass();
        String methodName = "set" + mode + "ACRAConfig";
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(methodName, SharedPreferences.class);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new IllegalStateException(methodName + " not found");
    }


    @Test
    public void testDebugConfiguration() throws Exception {

        // Debug mode overrides all saved state so no setup needed
        setAcraConfig("Debug");
        assertArrayEquals("Debug logcat arguments not set correctly",
                mApp.getAcraCoreConfigBuilder().build().logcatArguments().toArray(),
                new ImmutableList<>(debugLogcatArguments).toArray());
        verifyDebugACRAPreferences();
    }

    private void verifyDebugACRAPreferences() {
        assertTrue("ACRA was not disabled correctly",
                getSharedPrefs()
                        .getBoolean(ACRA.PREF_DISABLE_ACRA, true));
        assertEquals("ACRA feedback was not turned off correctly",
                AnkiDroidApp.FEEDBACK_REPORT_NEVER,
                getSharedPrefs()
                        .getString(AnkiDroidApp.FEEDBACK_REPORT_KEY, "undefined"));
    }

    @Test
    public void testProductionConfigurationUserDisabled() throws Exception {

        // set up as if the user had prefs saved to disable completely
        setReportConfig(AnkiDroidApp.FEEDBACK_REPORT_NEVER);

        // ACRA initializes production logcat via annotation and we can't mock Build.DEBUG
        // That means we are restricted from verifying production logcat args and this is the debug case again
        setAcraConfig("Production");
        verifyDebugACRAPreferences();
    }

    @Test
    public void testProductionConfigurationUserAsk() throws Exception {
        // set up as if the user had prefs saved to ask
        setReportConfig(FEEDBACK_REPORT_ASK);

        // If the user is set to ask, then it's production, with interaction mode dialog
        setAcraConfig("Production");
        verifyACRANotDisabled();

        assertToastMessage(R.string.feedback_manual_toast_text);
        assertToastIsEnabled();
        assertDialogEnabledStatus("Dialog should be enabled", true);
    }

    @Test
    public void testCrashReportLimit() throws Exception {
        // To test ACRA switch on  reporting, plant a production tree, and trigger a report
        Timber.plant(new AnkiDroidApp.ProductionCrashReportingTree());

        // set up as if the user had prefs saved to full auto
        setReportConfig(FEEDBACK_REPORT_ALWAYS);

        // If the user is set to always, then it's production, with interaction mode toast
        // will be useful with ACRA 5.2.0
        setAcraConfig("Production");

        // The same class/method combo is only sent once, so we face a new method each time (should test that system later)
        Exception crash = new Exception("testCrashReportSend at " + System.currentTimeMillis());
        StackTraceElement[] trace = new StackTraceElement[] {
                new StackTraceElement("Class", "Method" + (int)System.currentTimeMillis(), "File", (int)System.currentTimeMillis())
        };
        crash.setStackTrace(trace);

        // one send should work
        CrashReportData crashData = new CrashReportDataFactory(getTestContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build()).createCrashData(new ReportBuilder().exception(crash));

        assertTrue(new LimitingReportAdministrator().shouldSendReport(
                getTestContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build(),
                crashData)
        );

        // A second send should not work
        assertFalse(new LimitingReportAdministrator().shouldSendReport(
                getTestContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build(),
                crashData)
        );

        // Now let's clear data
        AnkiDroidApp.deleteACRALimiterData(getTestContext());

        // A third send should work again
        assertTrue(new LimitingReportAdministrator().shouldSendReport(
                getTestContext(),
                AnkiDroidApp.getInstance().getAcraCoreConfigBuilder().build(),
                crashData)
        );
    }


    @Test
    public void testProductionConfigurationUserAlways() throws Exception {
        // set up as if the user had prefs saved to full auto
        setReportConfig(FEEDBACK_REPORT_ALWAYS);

        // If the user is set to always, then it's production, with interaction mode toast
        setAcraConfig("Production");
        verifyACRANotDisabled();

        assertToastMessage(R.string.feedback_auto_toast_text);
        assertToastIsEnabled();
        assertDialogEnabledStatus("Dialog should not be enabled", false);
    }


    @Test
    public void testDialogEnabledWhenMovingFromAlwaysToAsk() throws Exception {
        // Raised in #6891 - we ned to ensure that the dialog is re-enabled after this transition.
        setReportConfig(FEEDBACK_REPORT_ALWAYS);

        // If the user is set to ask, then it's production, with interaction mode dialog
        setAcraConfig("Production");
        verifyACRANotDisabled();

        assertDialogEnabledStatus("dialog should be disabled when status is ALWAYS", false);
        assertToastMessage(R.string.feedback_auto_toast_text);

        setAcraReportingMode(FEEDBACK_REPORT_ASK);

        assertDialogEnabledStatus("dialog should be re-enabled after changed to ASK", true);
        assertToastMessage(R.string.feedback_manual_toast_text);
    }

    @Test
    public void testToastTextWhenMovingFromAskToAlways() throws Exception {
        // Raised in #6891 - we ned to ensure that the text is fixed after this transition.
        setReportConfig(FEEDBACK_REPORT_ASK);

        // If the user is set to ask, then it's production, with interaction mode dialog
        setAcraConfig("Production");
        verifyACRANotDisabled();

        assertToastMessage(R.string.feedback_manual_toast_text);

        setAcraReportingMode(FEEDBACK_REPORT_ALWAYS);

        assertToastMessage(R.string.feedback_auto_toast_text);
    }


    private void setAcraReportingMode(String feedbackReportAlways) {
        AnkiDroidApp.getInstance().setAcraReportingMode(feedbackReportAlways);
    }


    private void assertDialogEnabledStatus(String message, boolean isEnabled) throws ACRAConfigurationException {
        CoreConfiguration config = mApp.getAcraCoreConfigBuilder().build();
        for (Configuration configuration : config.pluginConfigurations()) {
            // Make sure the dialog is set to pop up
            if (configuration.getClass().toString().contains("Dialog")) {
                assertThat(message, configuration.enabled(), is(isEnabled));
            }
        }
    }


    private void assertToastIsEnabled() throws ACRAConfigurationException {
        CoreConfiguration config = mApp.getAcraCoreConfigBuilder().build();
        for (Configuration configuration : config.pluginConfigurations()) {

            if (configuration.getClass().toString().contains("Toast")) {
                assertThat("Toast should be enabled", configuration.enabled(), is(true));
            }
        }
    }

    private void assertToastMessage(@StringRes int res) throws ACRAConfigurationException {
        CoreConfiguration config = mApp.getAcraCoreConfigBuilder().build();
        for (Configuration configuration : config.pluginConfigurations()) {

            if (configuration.getClass().toString().contains("Toast")) {
                assertEquals(mApp.getResources().getString(res),
                        ((ToastConfiguration)configuration).text());
                assertTrue("Toast should be enabled", configuration.enabled());
            }
        }
    }

    private void verifyACRANotDisabled() {
        assertFalse("ACRA was not enabled correctly",
                getSharedPrefs().getBoolean(ACRA.PREF_DISABLE_ACRA, false));
    }


    private void setAcraConfig(String production) throws Exception {
        setAcraConfig(production, getSharedPrefs());
    }


    private void setReportConfig(String feedbackReportAsk) {
        getSharedPrefs().edit()
                .putString(AnkiDroidApp.FEEDBACK_REPORT_KEY, feedbackReportAsk).commit();
    }


    private SharedPreferences getSharedPrefs() {
        return AnkiDroidApp.getSharedPrefs(getTestContext());
    }
}
