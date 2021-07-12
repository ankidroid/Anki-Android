/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.dialogs

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.UIUtils
import com.ichi2.anki.analytics.Acra
import com.ichi2.anki.analytics.CriticalException
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.model.UrlOpener
import net.ankiweb.rsdroid.RustBackendFailedException
import org.acra.ACRA
import org.acra.ErrorReporter
import org.acra.util.Installation
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.containsString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor

// There is an undiagnosed error about unclosed Closeables in this class
@RunWith(AndroidJUnit4::class)
class CriticalExceptionDialogTest : RobolectricTest() {
    private lateinit var mUrlOpener: UrlOpener
    private lateinit var mListener: CriticalExceptionDialog.CriticalExceptionDialogClosedListener
    private lateinit var mException: CriticalException

    @Before
    fun before() {
        CriticalException.reset()
    }

    @Test
    fun dialog_cant_be_closed_by_pressing_outside() {
        regularAssert { dialog ->
            val asMaterial = dialog.dialog as MaterialDialog
            assertPrivateField(asMaterial.builder, "cancelable", false)
            assertPrivateField(asMaterial.builder, "canceledOnTouchOutside", false)
        }
    }

    @Test
    fun analytics_is_invisible_if_analytics_enabled() {
        onAnalytics(true) {
            dialog ->
            assertThat(dialog.getEnableAnalyticsCheckbox().visibility, `is`(View.GONE))
        }
    }

    @Test
    fun analytics_is_visible_if_analytics_disabled() {
        onAnalytics(false) {
            dialog ->
            assertThat(dialog.getEnableAnalyticsCheckbox().visibility, `is`(View.VISIBLE))
        }
    }

    @Test
    fun error_reporting_not_visible_if_already_enabled() {
        onReporting(true) {
            dialog ->
            assertThat(dialog.getEnableErrorReportingCheckbox().visibility, `is`(View.GONE))
        }
    }

    @Test
    fun reporting_disabled() {
        onReporting(false) {
            dialog ->
            assertThat(dialog.getEnableErrorReportingCheckbox().visibility, `is`(View.VISIBLE))
        }
    }

    @Test
    fun toast_displayed_if_reporting_enabled() {
        onReporting(false) {
            dialog ->
            run {
                mockStatic(UIUtils::class.java).use { ui ->
                    dialog.getEnableErrorReportingCheckbox().isChecked = true
                    assertToastDisplayed(ui)
                }
            }
        }
    }

    @Test
    fun no_toast_if_reporting_disabled() {
        onReporting(false) {
            dialog ->
            run {
                mockStatic(UIUtils::class.java).use { ui ->
                    dialog.getEnableErrorReportingCheckbox().isChecked = false
                    assertNoToastDisplayed(ui)
                }
            }
        }
    }

    @Test
    fun default_close_does_not_enable_any_telemetry() {
        // Both analytics and reporting are disabled, so the checkboxes appear
        // We should onl enable telemetry if they check the boxes
        Acra.disableAcra(targetContext)
        regularAssert { dialog ->
            run {
                assertThat(dialog.getEnableAnalyticsCheckbox().visibility, `is`(View.VISIBLE))
                assertThat(dialog.getEnableErrorReportingCheckbox().visibility, `is`(View.VISIBLE))

                dialog.callClose()

                verify(mListener, times(1)).onCriticalExceptionDialogClosed()

                assertThat("analytics should still be disabled", UsageAnalytics.isEnabled(), `is`(false))
                assertThat("ACRA should still be disabled", AnkiDroidApp.isAcraEnabled(targetContext, true), `is`(false))
            }
        }
    }

    @Test
    fun debug_info_contains_exception() {
        regularAssert { dialog ->
            run {
                val debugInfo = dialog.getDebugInfo()
                assertThat(debugInfo, containsString(this.mException.getException().javaClass.simpleName))
                assertThat(debugInfo, containsString(this.mException.getException().message))
            }
        }
    }

    fun debug_info_contains_acra_id() {
        regularAssert { dialog ->
            run {
                val debugInfo = dialog.getDebugInfo()
                assertThat(debugInfo, containsString(Installation.id(targetContext)))
                assertThat(debugInfo, containsString("ACRA"))
            }
        }
    }

    @Test
    fun analytics_close_enables_analytics() {
        regularAssert { dialog ->
            run {
                dialog.getEnableAnalyticsCheckbox().isChecked = true
                dialog.callClose()

                assertThat(UsageAnalytics.isEnabled(), `is`(true))
            }
        }
    }

    @Test
    fun if_reporting_enabled_on_close_reporting_is_enabled() {
        regularAssert { dialog ->
            run {
                dialog.getEnableErrorReportingCheckbox().isChecked = true
                // This hung on line: executor.performInteractions(reportFile)
                mockStatic(ACRA::class.java).use { acra ->
                    acra.`when`<Any> { ACRA.getErrorReporter() }.thenReturn(mock(ErrorReporter::class.java))
                    dialog.callClose()
                    assertThat("ACRA should now be enabled", AnkiDroidApp.isAcraEnabled(targetContext, false), `is`(true))
                }
            }
        }
    }

    @Test
    fun if_reporting_enabled_on_close_report_is_sent() {
        regularAssert { dialog ->
            run {
                dialog.getEnableErrorReportingCheckbox().isChecked = true
                // This hung on line: executor.performInteractions(reportFile)
                mockStatic(ACRA::class.java).use { acra ->
                    val reporter = mock(ErrorReporter::class.java)
                    acra.`when`<Any> { ACRA.getErrorReporter() }.thenReturn(reporter)
                    dialog.callClose()
                    verify(reporter, times(1)).handleException(mException.getException())
                }
            }
        }
    }

    @Test
    fun test_listener_is_closed_on_close() {
        regularAssert { dialog ->
            run {
                verify(mListener, never()).onCriticalExceptionDialogClosed()
                dialog.callClose()
                verify(mListener, times(1)).onCriticalExceptionDialogClosed()
            }
        }
    }

    @Test
    fun test_ignore_cancel_does_not_close_dialog() {
        regularAssert { dialog ->
            run {
                val ignoreDialog = dialog.callIgnore()
                ignoreDialog.getActionButton(DialogAction.NEGATIVE).callOnClick()
                verify(mListener, never()).onCriticalExceptionDialogClosed()
            }
        }
    }

    @Test
    fun test_ignore_accept_does_close_dialog() {
        regularAssert { dialog ->
            run {
                val ignoreDialog = dialog.callIgnore()
                ignoreDialog.getActionButton(DialogAction.POSITIVE).callOnClick()
                verify(mListener, times(1)).onCriticalExceptionDialogClosed()
            }
        }
    }

    @Test
    fun test_not_closed_on_contact() {
        regularAssert { dialog ->
            run {
                dialog.callContactDevelopers()
                verify(mListener, never()).onCriticalExceptionDialogClosed()
            }
        }
    }

    @Test
    fun test_contact_known_issue() {
        this.mException = CriticalException.from(RustBackendFailedException(UnsatisfiedLinkError(".dynamic section header was not found")))
        regularAssert { dialog ->
            run {
                dialog.callContactDevelopers()
                assertThat("supplied issue id has not changed", mException.getIssue().gitHubIssueId, `is`(9064))
                verify(mUrlOpener, times(1)).openUrl(Uri.parse("https://github.com/ankidroid/Anki-Android/issues/9064"))
            }
        }
    }

    @Test
    fun test_contact_unknown_issue_unsatisfied_link() {
        this.mException = CriticalException.from(RustBackendFailedException(UnsatisfiedLinkError()))
        // Warning: this can be flaky due to the line numbers in the stack trace
        regularAssert { dialog ->
            run {
                dialog.callContactDevelopers()
                assertThat(mException.getException().cause!!.message.toString(), `is`("null"))
                val expected = "https://github.com/ankidroid/Anki-Android/issues/new?template=issue_template.md&title=%5BCritical%20Exception%5D%20java.lang.UnsatisfiedLinkError&body=%60%60%60%0Anet.ankiweb.rsdroid.RustBackendFailedException%3A%20java.lang.UnsatisfiedLinkError%0D%0A%09at%20com.ichi2.anki.dialogs.CriticalExceptionDialogTest.test_contact_unknown_issue_unsatisfied_link%28CriticalExceptionDialogTest.kt%3A257%29%0D%0A%09at%20java.base%2Fjdk.internal.reflect.NativeMethodAccessorImpl.invoke0%28Native%20Method%29%0D%0A%09at%20java.base%2Fjdk.internal.reflect.NativeMethodAccessorImpl.invoke%28NativeMethodAccessorImpl.java%3A62%29%0D%0A%09at%20java.base%2Fjdk.internal.reflect.DelegatingMethodAccessorImpl.invoke%28DelegatingMethodAccessorImpl.java%3A43%29%0D%0A%09at%20java.base%2Fjava.lang.reflect.Method.invoke%28Method.java%3A566%29%0D%0A%09at%20org.junit.runners.model.FrameworkMethod%241.runReflectiveCall%28FrameworkMethod.java%3A59%29%0D%0A%09at%20org.junit.internal.runners.model.ReflectiveCallable.run%28ReflectiveCallable.java%3A12%29%0D%0A%09at%20org.junit.runners.model.FrameworkMethod.invokeExplosively%28FrameworkMethod.java%3A56%29%0D%0A%09at%20org.junit.internal.runners.statements.InvokeMethod.evaluate%28InvokeMethod.java%3A17%29%0D%0A%09at%20org.junit.internal.runners.statements.RunBefores.evaluate%28RunBefores.java%3A26%29%0D%0A%09at%20org.junit.internal.runners.statements.RunAfters.evaluate%28RunAfters.java%3A27%29%0D%0A%09at%20org.junit.runners.ParentRunner%243.evaluate%28ParentRunner.java%3A306%29%0D%0A%09at%20org.robolectric.RobolectricTestRunner%24HelperTestRunner%241.evaluate%28RobolectricTestRunner.java%3A575%29%0D%0A%09at%20org.robolectric.internal.SandboxTestRunner%242.lambda%24evaluate%240%28SandboxTestRunner.java%3A278%29%0D%0A%09at%20org.robolectric.internal.bytecode.Sandbox.lambda%24runOnMainThread%240%28Sandbox.java%3A89%29%0D%0A%09at%20java.base%2Fjava.util.concurrent.FutureTask.run%28FutureTask.java%3A264%29%0D%0A%09at%20java.base%2Fjava.util.concurrent.ThreadPoolExecutor.runWorker%28ThreadPoolExecutor.java%3A1128%29%0D%0A%09at%20java.base%2Fjava.util.concurrent.ThreadPoolExecutor%24Worker.run%28ThreadPoolExecutor.java%3A628%29%0D%0A%09at%20java.base%2Fjava.lang.Thread.run%28Thread.java%3A834%29%0D%0ACaused%20by%3A%20java.lang.UnsatisfiedLinkError%0D%0A%09...%2019%20more%0D%0A%0A%60%60%60"
                assertThat("no null in title", expected, not(containsString("null")))
                assertThat("no newline in title", expected, not(containsString("UnsatisfiedLinkError%0A")))
                assertUrlOpened(expected)
            }
        }
    }

    @Test
    fun test_contact_long_issue() {
        val longExceptionMessage = "a".repeat(10000)
        this.mException = CriticalException.from(RustBackendFailedException(UnsatisfiedLinkError(longExceptionMessage)))
        regularAssert { dialog ->
            run {
                dialog.callContactDevelopers()
                val message = "a".repeat(8021)
                val expected = "https://github.com/ankidroid/Anki-Android/issues/new?template=issue_template.md&title=%5BCritical%20Exception%5D%20java.lang.UnsatisfiedLinkError%3A%20$message...%0A%60%60%60"
                assertUrlOpened(expected)
            }
        }
    }

    private fun onReporting(enableReporting: Boolean, function: (fragment: CriticalExceptionDialog) -> Unit) {
        // HACK: We should mock here, but I couldn't manage to get it working with Mockito
        if (enableReporting) {
            Acra.enableWithAskDialog(targetContext)
        } else {
            Acra.disableAcra(targetContext)
        }
        regularAssert(function)
    }

    private fun onAnalytics(enableAnalytics: Boolean, function: (fragment: CriticalExceptionDialog) -> Unit) {
        mockStatic(UsageAnalytics::class.java).use { analytics ->
            analytics.`when`<Any> { UsageAnalytics.isEnabled() }.thenReturn(enableAnalytics)
            regularAssert(function)
        }
    }

    private fun regularAssert(assertion: (fragment: CriticalExceptionDialog) -> Unit) {
        if (!this::mException.isInitialized) {
            mException = CriticalException.from(IllegalStateException("uniqueExString"))
        }
        this.mUrlOpener = mock(UrlOpener::class.java)
        CriticalException.register(mException)
        this.mListener = mock(CriticalExceptionDialog.CriticalExceptionDialogClosedListener::class.java)
        val factory = CriticalExceptionDialog.Factory(mListener, this, mUrlOpener)
        val scenario = FragmentScenario.launch(CriticalExceptionDialog::class.java, Bundle(), factory)

        scenario.moveToState(Lifecycle.State.STARTED)

        scenario.onFragment(assertion)
    }

    private fun <T> assertPrivateField(builder: T?, fieldName: String, @Suppress("SameParameterValue") expected: Boolean) {

        val cancellableField = builder!!::class.java.getDeclaredField(fieldName)
        cancellableField.isAccessible = true
        assertThat(cancellableField.getBoolean(builder), `is`(expected))
    }

    private fun assertToastDisplayed(ui: MockedStatic<UIUtils>) {
        ui.verify({ UIUtils.showThemedToast(any(Context::class.java), anyInt(), anyBoolean()) }, times(1))
    }

    private fun assertNoToastDisplayed(ui: MockedStatic<UIUtils>) {
        ui.verify({ UIUtils.showThemedToast(any(Context::class.java), anyInt(), anyBoolean()) }, never())
    }

    private fun CriticalExceptionDialog.callClose() = this.getActionButton(DialogAction.NEUTRAL).callOnClick()
    private fun CriticalExceptionDialog.callIgnore(): MaterialDialog {
        this.getActionButton(DialogAction.NEGATIVE).callOnClick()
        return this.mLastIgnoreDialog!!
    }

    private fun CriticalExceptionDialog.callContactDevelopers() = this.getActionButton(DialogAction.POSITIVE).callOnClick()

    private fun assertUrlOpened(expected: String) {
        val argument = argumentCaptor<Uri>()
        verify(mUrlOpener, times(1)).openUrl(argument.capture())
        assertThat(argument.firstValue.toString(), `is`(expected))
    }
}
