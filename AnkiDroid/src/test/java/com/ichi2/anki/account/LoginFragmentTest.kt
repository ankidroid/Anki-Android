// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.account

import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.ui.TextInputEditField
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
class LoginFragmentTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        BackendEmulatingOpenConflict.enable()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        BackendEmulatingOpenConflict.disable()
    }

    /**
     * #21051: the locked-collection guidance is multi-sentence, so it is shown in a dialog
     * rather than truncated in the login screen's snackbar
     */
    @Test
    fun `login with a locked collection shows guidance`() {
        throwOnShowError = false
        val activity = startRegularActivity<AccountActivity>(AccountActivity.getIntent(targetContext))
        activity.findViewById<TextInputEditText>(R.id.username).setText("user@example.com")
        activity.findViewById<TextInputEditField>(R.id.password).setText("hunter2")

        activity.findViewById<Button>(R.id.login_button).performClick()
        activity.waitForLoginToFinish()

        assertThat(getAlertDialogText(true), containsString("Advanced settings"))
    }

    private fun AccountActivity.waitForLoginToFinish() {
        val start = TimeSource.Monotonic.markNow()
        while (window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0) {
            check(start.elapsedNow() < 10.seconds) { "login did not finish" }
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
