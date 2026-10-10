// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.EmptyAnkiActivity
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** Tests for [MeteredSyncPolicy] */
@RunWith(RobolectricTestRunner::class)
class MeteredSyncPolicyTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        mockkObject(MeteredSyncPolicy)
    }

    @After
    override fun tearDown() {
        unmockkObject(MeteredSyncPolicy)
        super.tearDown()
    }

    @Test
    fun `runs onConfirm immediately when not blocked`() {
        every { MeteredSyncPolicy.shouldBlock() } returns false

        val result = attemptMeteredSync()

        assertThat("onDialogShown not called", result.dialogShown, equalTo(false))
        assertThat("onConfirm ran", result.onConfirmCalled, equalTo(true))
        assertThat(result.permission, equalTo(MeteredSyncPermission.USE_PREFERENCES))
        assertThat("no dialog shown", result.dialog, nullValue())
    }

    @Test
    fun `shows dialog and notifies onDialogShown when blocked`() {
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync()

        assertThat("onDialogShown invoked", result.dialogShown, equalTo(true))
        assertThat("onConfirm not yet run", result.onConfirmCalled, equalTo(false))
        assertThat("dialog shown", result.dialog, notNullValue())
    }

    @Test
    fun `Continue runs onConfirm`() {
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync()
        assertThat("onConfirm not called initially", result.onConfirmCalled, equalTo(false))

        result.clickContinue()

        assertThat("onConfirm ran after Continue", result.onConfirmCalled, equalTo(true))
        assertThat(result.permission, equalTo(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME))
    }

    @Test
    fun `Cancel does not run onConfirm`() {
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync()
        assertThat("onConfirm not called initially", result.onConfirmCalled, equalTo(false))

        result.clickCancel()

        assertThat("onConfirm not run after Cancel", result.onConfirmCalled, equalTo(false))
    }

    @Test
    fun `skipPrompt skips prompt even when metered`() {
        // Issue 20674
        every { MeteredSyncPolicy.shouldBlock() } returns true

        val result = attemptMeteredSync(skipPrompt = true)

        assertThat("onConfirm ran immediately", result.onConfirmCalled, equalTo(true))
        assertThat("no dialog shown", result.dialog, nullValue())
        assertThat("onDialogShown not called", result.dialogShown, equalTo(false))
        assertThat(result.permission, equalTo(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME))
    }

    @Test
    fun `skipPrompt on unmetered network runs directly`() {
        every { MeteredSyncPolicy.shouldBlock() } returns false

        val result = attemptMeteredSync(skipPrompt = true)

        assertThat("onConfirm ran immediately", result.onConfirmCalled, equalTo(true))
        assertThat("no dialog shown", result.dialog, nullValue())
        assertThat(result.permission, equalTo(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME))
    }

    @Test
    fun `setAlwaysAllow persists choice`() {
        unmockkObject(MeteredSyncPolicy)
        Prefs.allowSyncOnMeteredConnections = false
        MeteredSyncPolicy.setAlwaysAllow(true)
        assertThat(Prefs.allowSyncOnMeteredConnections, equalTo(true))
    }

    /** Invokes [MeteredSyncPolicy.confirmThen] from a freshly-built activity. */
    private fun attemptMeteredSync(skipPrompt: Boolean = false): MeteredSyncAttemptResult {
        var dialogShown = false
        var permission: MeteredSyncPermission? = null
        with(startRegularActivity<EmptyAnkiActivity>()) {
            MeteredSyncPolicy.confirmThen(
                skipPrompt = skipPrompt,
                onDialogShown = { dialogShown = true },
            ) { permission = it }
        }
        return MeteredSyncAttemptResult(
            dialogShown = dialogShown,
            getPermission = { permission },
            dialog = ShadowDialog.getLatestDialog() as? AlertDialog,
        )
    }

    /**
     * Tracks the side effects of a single call to [MeteredSyncPolicy.confirmThen].
     *
     * @property dialogShown whether the warning dialog's `onDialogShown` callback fired
     * @property onConfirmCalled whether `onConfirm` has been invoked
     * @property permission the permission received by `onConfirm`, or `null` before it runs
     * @property dialog the most recently displayed [AlertDialog], or `null` if none was shown
     */
    private class MeteredSyncAttemptResult(
        val dialogShown: Boolean,
        private val getPermission: () -> MeteredSyncPermission?,
        val dialog: AlertDialog?,
    ) {
        val permission: MeteredSyncPermission? get() = getPermission()
        val onConfirmCalled: Boolean get() = permission != null

        fun clickContinue() = clickDialogButton(DialogInterface.BUTTON_POSITIVE)

        fun clickCancel() = clickDialogButton(DialogInterface.BUTTON_NEGATIVE)

        private fun clickDialogButton(button: Int) {
            dialog!!.getButton(button).performClick()
            ShadowLooper.runUiThreadTasks()
        }
    }
}
