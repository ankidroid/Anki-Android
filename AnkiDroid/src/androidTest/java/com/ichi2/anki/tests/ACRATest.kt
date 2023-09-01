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
package com.ichi2.anki.tests

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.CrashReportService.FEEDBACK_REPORT_ALWAYS
import com.ichi2.anki.CrashReportService.FEEDBACK_REPORT_ASK
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.testutil.GrantStoragePermission
import org.acra.ACRA
import org.acra.builder.ReportBuilder
import org.acra.config.ACRAConfigurationException
import org.acra.config.LimitingReportAdministrator
import org.acra.config.ToastConfiguration
import org.acra.data.CrashReportDataFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
@SuppressLint("DirectSystemCurrentTimeMillisUsage")
class ACRATest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance
    private var mApp: AnkiDroidApp? = null
    private val mDebugLogcatArguments = arrayOf("-t", "1500", "-v", "long", "ACRA:S")

    // private String[] prodLogcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" };
    @Before
    @UiThreadTest
    fun setUp() {
        mApp = testContext.applicationContext as AnkiDroidApp
        // Note: attachBaseContext can't be called twice as we're using the same instance between all tests.
        mApp!!.onCreate()
    }

    @Test
    @Throws(Exception::class)
    fun testDebugConfiguration() {
        // Debug mode overrides all saved state so no setup needed
        CrashReportService.setDebugACRAConfig(sharedPrefs)
        assertArrayEquals(
            "Debug logcat arguments not set correctly",
            CrashReportService.acraCoreConfigBuilder.build().logcatArguments.toTypedArray(),
            mDebugLogcatArguments
        )
        verifyDebugACRAPreferences()
    }

    private fun verifyDebugACRAPreferences() {
        assertTrue(
            "ACRA was not disabled correctly",
            sharedPrefs
                .getBoolean(ACRA.PREF_DISABLE_ACRA, true)
        )
        assertEquals(
            "ACRA feedback was not turned off correctly",
            CrashReportService.FEEDBACK_REPORT_NEVER,
            sharedPrefs
                .getString(CrashReportService.FEEDBACK_REPORT_KEY, "undefined")
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProductionConfigurationUserDisabled() {
        // set up as if the user had prefs saved to disable completely
        setReportConfig(CrashReportService.FEEDBACK_REPORT_NEVER)

        // ACRA initializes production logcat via annotation and we can't mock Build.DEBUG
        // That means we are restricted from verifying production logcat args and this is the debug case again
        CrashReportService.setProductionACRAConfig(sharedPrefs)
        verifyDebugACRAPreferences()
    }

    @Test
    @Throws(Exception::class)
    fun testProductionConfigurationUserAsk() {
        // set up as if the user had prefs saved to ask
        setReportConfig(FEEDBACK_REPORT_ASK)

        // If the user is set to ask, then it's production, with interaction mode dialog
        CrashReportService.setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertToastMessage(R.string.feedback_for_manual_toast_text)
        assertToastIsEnabled()
        assertDialogEnabledStatus("Dialog should be enabled", true)
    }

    @Test
    @Throws(Exception::class)
    fun testCrashReportLimit() {
        // To test ACRA switch on  reporting, plant a production tree, and trigger a report
        Timber.plant(AnkiDroidApp.ProductionCrashReportingTree())

        // set up as if the user had prefs saved to full auto
        setReportConfig(FEEDBACK_REPORT_ALWAYS)

        // If the user is set to always, then it's production, with interaction mode toast
        // will be useful with ACRA 5.2.0
        CrashReportService.setProductionACRAConfig(sharedPrefs)

        // The same class/method combo is only sent once, so we face a new method each time (should test that system later)
        val crash = Exception("testCrashReportSend at " + System.currentTimeMillis())
        val trace = arrayOf(
            StackTraceElement(
                "Class",
                "Method" + System.currentTimeMillis().toInt(),
                "File",
                System.currentTimeMillis().toInt()
            )
        )
        crash.stackTrace = trace

        // one send should work
        val crashData = CrashReportDataFactory(
            testContext,
            CrashReportService.acraCoreConfigBuilder.build()
        ).createCrashData(ReportBuilder().exception(crash))
        assertTrue(
            LimitingReportAdministrator().shouldSendReport(
                testContext,
                CrashReportService.acraCoreConfigBuilder.build(),
                crashData
            )
        )

        // A second send should not work
        assertFalse(
            LimitingReportAdministrator().shouldSendReport(
                testContext,
                CrashReportService.acraCoreConfigBuilder.build(),
                crashData
            )
        )

        // Now let's clear data
        CrashReportService.deleteACRALimiterData(testContext)

        // A third send should work again
        assertTrue(
            LimitingReportAdministrator().shouldSendReport(
                testContext,
                CrashReportService.acraCoreConfigBuilder.build(),
                crashData
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProductionConfigurationUserAlways() {
        // set up as if the user had prefs saved to full auto
        setReportConfig(FEEDBACK_REPORT_ALWAYS)

        // If the user is set to always, then it's production, with interaction mode toast
        CrashReportService.setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertToastMessage(R.string.feedback_auto_toast_text)
        assertToastIsEnabled()
        assertDialogEnabledStatus("Dialog should not be enabled", false)
    }

    @Test
    @Throws(Exception::class)
    fun testDialogEnabledWhenMovingFromAlwaysToAsk() {
        // Raised in #6891 - we ned to ensure that the dialog is re-enabled after this transition.
        setReportConfig(FEEDBACK_REPORT_ALWAYS)

        // If the user is set to ask, then it's production, with interaction mode dialog
        CrashReportService.setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertDialogEnabledStatus("dialog should be disabled when status is ALWAYS", false)
        assertToastMessage(R.string.feedback_auto_toast_text)

        setAcraReportingMode(FEEDBACK_REPORT_ASK)

        assertDialogEnabledStatus("dialog should be re-enabled after changed to ASK", true)
        assertToastMessage(R.string.feedback_for_manual_toast_text)
    }

    @Test
    @Throws(Exception::class)
    fun testToastTextWhenMovingFromAskToAlways() {
        // Raised in #6891 - we ned to ensure that the text is fixed after this transition.
        setReportConfig(FEEDBACK_REPORT_ASK)

        // If the user is set to ask, then it's production, with interaction mode dialog
        CrashReportService.setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertToastMessage(R.string.feedback_for_manual_toast_text)

        setAcraReportingMode(FEEDBACK_REPORT_ALWAYS)

        assertToastMessage(R.string.feedback_auto_toast_text)
    }

    private fun setAcraReportingMode(feedbackReportAlways: String) {
        CrashReportService.setAcraReportingMode(feedbackReportAlways)
    }

    @Throws(ACRAConfigurationException::class)
    private fun assertDialogEnabledStatus(message: String, isEnabled: Boolean) {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        for (configuration in config.pluginConfigurations) {
            // Make sure the dialog is set to pop up
            if (configuration.javaClass.toString().contains("Dialog")) {
                assertThat(message, configuration.enabled(), equalTo(isEnabled))
            }
        }
    }

    @Throws(ACRAConfigurationException::class)
    private fun assertToastIsEnabled() {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        for (configuration in config.pluginConfigurations) {
            if (configuration.javaClass.toString().contains("Toast")) {
                assertThat("Toast should be enabled", configuration.enabled(), equalTo(true))
            }
        }
    }

    @Throws(ACRAConfigurationException::class)
    private fun assertToastMessage(@StringRes res: Int) {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        for (configuration in config.pluginConfigurations) {
            if (configuration.javaClass.toString().contains("Toast")) {
                assertEquals(
                    mApp!!.resources.getString(res),
                    (configuration as ToastConfiguration).text
                )
                assertTrue("Toast should be enabled", configuration.enabled())
            }
        }
    }

    private fun verifyACRANotDisabled() {
        assertFalse(
            "ACRA was not enabled correctly",
            sharedPrefs.getBoolean(ACRA.PREF_DISABLE_ACRA, false)
        )
    }

    private fun setReportConfig(feedbackReportAsk: String) {
        sharedPrefs.edit { putString(CrashReportService.FEEDBACK_REPORT_KEY, feedbackReportAsk) }
    }

    private val sharedPrefs: SharedPreferences
        get() = testContext.sharedPrefs()
}
