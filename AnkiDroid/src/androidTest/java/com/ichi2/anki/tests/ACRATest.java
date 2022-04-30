/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2018 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.anki.tests;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;
import androidx.test.annotation.UiThreadTest;
import androidx.test.rule.GrantPermissionRule;

import com.ichi2.anki.CrashReportService;
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

import static com.ichi2.anki.CrashReportService.FEEDBACK_REPORT_ALWAYS;
import static com.ichi2.anki.CrashReportService.FEEDBACK_REPORT_ASK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
@SuppressLint("DirectSystemCurrentTimeMillisUsage")
public class ACRATest extends InstrumentedTest {

    @Rule public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    private AnkiDroidApp mApp = null;

    private final String[] mDebugLogcatArguments = { "-t", "300", "-v", "long", "ACRA:S"};
    //private String[] prodLogcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" };


    @Before
    @UiThreadTest
    public void setUp() {
        mApp = (AnkiDroidApp) getTestContext().getApplicationContext();
        // Note: attachBaseContext can't be called twice as we're using the same instance between all tests.
        mApp.onCreate();
    }

    @Test
    public void testDebugConfiguration() throws Exception {

        // Debug mode overrides all saved state so no setup needed
        CrashReportService.setDebugACRAConfig(getSharedPrefs());
        assertArrayEquals("Debug logcat arguments not set correctly",
                CrashReportService.getAcraCoreConfigBuilder().build().logcatArguments().toArray(),
                new ImmutableList<>(mDebugLogcatArguments).toArray());
        verifyDebugACRAPreferences();
    }

    private void verifyDebugACRAPreferences() {
        assertTrue("ACRA was not disabled correctly",
                getSharedPrefs()
                        .getBoolean(ACRA.PREF_DISABLE_ACRA, true));
        assertEquals("ACRA feedback was not turned off correctly",
                CrashReportService.FEEDBACK_REPORT_NEVER,
                getSharedPrefs()
                        .getString(CrashReportService.FEEDBACK_REPORT_KEY, "undefined"));
    }

    @Test
    public void testProductionConfigurationUserDisabled() throws Exception {

        // set up as if the user had prefs saved to disable completely
        setReportConfig(CrashReportService.FEEDBACK_REPORT_NEVER);

        // ACRA initializes production logcat via annotation and we can't mock Build.DEBUG
        // That means we are restricted from verifying production logcat args and this is the debug case again
        CrashReportService.setProductionACRAConfig(getSharedPrefs());
        verifyDebugACRAPreferences();
    }

    @Test
    public void testProductionConfigurationUserAsk() throws Exception {
        // set up as if the user had prefs saved to ask
        setReportConfig(FEEDBACK_REPORT_ASK);

        // If the user is set to ask, then it's production, with interaction mode dialog
        CrashReportService.setProductionACRAConfig(getSharedPrefs());
        verifyACRANotDisabled();

        assertToastMessage(R.string.feedback_for_manual_toast_text);
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
        CrashReportService.setProductionACRAConfig(getSharedPrefs());

        // The same class/method combo is only sent once, so we face a new method each time (should test that system later)
        Exception crash = new Exception("testCrashReportSend at " + System.currentTimeMillis());
        StackTraceElement[] trace = new StackTraceElement[] {
                new StackTraceElement("Class", "Method" + (int)System.currentTimeMillis(), "File", (int)System.currentTimeMillis())
        };
        crash.setStackTrace(trace);

        // one send should work
        CrashReportData crashData = new CrashReportDataFactory(getTestContext(),
                CrashReportService.getAcraCoreConfigBuilder().build()).createCrashData(new ReportBuilder().exception(crash));

        assertTrue(new LimitingReportAdministrator().shouldSendReport(
                getTestContext(),
                CrashReportService.getAcraCoreConfigBuilder().build(),
                crashData)
        );

        // A second send should not work
        assertFalse(new LimitingReportAdministrator().shouldSendReport(
                getTestContext(),
                CrashReportService.getAcraCoreConfigBuilder().build(),
                crashData)
        );

        // Now let's clear data
        CrashReportService.deleteACRALimiterData(getTestContext());

        // A third send should work again
        assertTrue(new LimitingReportAdministrator().shouldSendReport(
                getTestContext(),
                CrashReportService.getAcraCoreConfigBuilder().build(),
                crashData)
        );
    }


    @Test
    public void testProductionConfigurationUserAlways() throws Exception {
        // set up as if the user had prefs saved to full auto
        setReportConfig(FEEDBACK_REPORT_ALWAYS);

        // If the user is set to always, then it's production, with interaction mode toast
        CrashReportService.setProductionACRAConfig(getSharedPrefs());
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
        CrashReportService.setProductionACRAConfig(getSharedPrefs());
        verifyACRANotDisabled();

        assertDialogEnabledStatus("dialog should be disabled when status is ALWAYS", false);
        assertToastMessage(R.string.feedback_auto_toast_text);

        setAcraReportingMode(FEEDBACK_REPORT_ASK);

        assertDialogEnabledStatus("dialog should be re-enabled after changed to ASK", true);
        assertToastMessage(R.string.feedback_for_manual_toast_text);
    }

    @Test
    public void testToastTextWhenMovingFromAskToAlways() throws Exception {
        // Raised in #6891 - we ned to ensure that the text is fixed after this transition.
        setReportConfig(FEEDBACK_REPORT_ASK);

        // If the user is set to ask, then it's production, with interaction mode dialog
        CrashReportService.setProductionACRAConfig(getSharedPrefs());
        verifyACRANotDisabled();

        assertToastMessage(R.string.feedback_for_manual_toast_text);

        setAcraReportingMode(FEEDBACK_REPORT_ALWAYS);

        assertToastMessage(R.string.feedback_auto_toast_text);
    }


    private void setAcraReportingMode(String feedbackReportAlways) {
        CrashReportService.setAcraReportingMode(feedbackReportAlways);
    }


    private void assertDialogEnabledStatus(String message, boolean isEnabled) throws ACRAConfigurationException {
        CoreConfiguration config = CrashReportService.getAcraCoreConfigBuilder().build();
        for (Configuration configuration : config.pluginConfigurations()) {
            // Make sure the dialog is set to pop up
            if (configuration.getClass().toString().contains("Dialog")) {
                assertThat(message, configuration.enabled(), is(isEnabled));
            }
        }
    }


    private void assertToastIsEnabled() throws ACRAConfigurationException {
        CoreConfiguration config = CrashReportService.getAcraCoreConfigBuilder().build();
        for (Configuration configuration : config.pluginConfigurations()) {

            if (configuration.getClass().toString().contains("Toast")) {
                assertThat("Toast should be enabled", configuration.enabled(), is(true));
            }
        }
    }

    private void assertToastMessage(@StringRes int res) throws ACRAConfigurationException {
        CoreConfiguration config = CrashReportService.getAcraCoreConfigBuilder().build();
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

    private void setReportConfig(String feedbackReportAsk) {
        getSharedPrefs().edit()
                .putString(CrashReportService.FEEDBACK_REPORT_KEY, feedbackReportAsk).commit();
    }

    private SharedPreferences getSharedPrefs() {
        return AnkiDroidApp.getSharedPrefs(getTestContext());
    }
}
