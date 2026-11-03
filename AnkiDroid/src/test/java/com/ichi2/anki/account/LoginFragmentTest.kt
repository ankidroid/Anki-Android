// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.account

import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.sync.SyncAuth
import anki.sync.SyncLoginRequest
import anki.sync.syncAuth
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.testutils.withQueuedCollectionAccess
import com.ichi2.ui.TextInputEditField
import kotlinx.coroutines.test.advanceUntilIdle
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncAuthFailedException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LoginFragmentTest : RobolectricTest() {
    @get:Rule
    val backend = BackendOverrideRule()

    /**
     * #21051: the locked-collection guidance is multi-sentence, so it is shown in a dialog
     * rather than truncated in the login screen's snackbar
     */
    @Test
    fun `login with a locked collection shows guidance`() =
        runTest {
            lockCollection()

            startLoginScreen().logIn()

            assertContains(getAlertDialogText(checkDismissed = true).orEmpty(), "Advanced settings")
        }

    @Test
    fun `retrying after a failed login blocks input until it finishes`() =
        runTest {
            failAuthentication()
            val login = startLoginScreen()
            login.logIn()
            assertFalse(login.get().isBlockingTouches)

            withQueuedCollectionAccess {
                login.logIn()
                assertTrue(login.get().isBlockingTouches, "input must be blocked while the retry runs")
                advanceUntilIdle()
            }
            advanceRobolectricLooper()

            assertFalse(login.get().isBlockingTouches)
        }

    @Test
    fun `login in progress blocks input after recreation`() =
        runTest {
            succeedAuthentication()
            val login = startLoginScreen()

            withQueuedCollectionAccess {
                login.logIn()
                login.recreate()
                assertTrue(login.get().isBlockingTouches, "the recreated screen must block input while logging in")
                advanceUntilIdle()
            }
            advanceRobolectricLooper()

            assertEquals(TEST_HKEY, Prefs.hkey)
            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    fun `logo is hidden in a compact landscape window`() {
        val login = startLoginScreen()

        assertFalse(login.get().findViewById<View>(R.id.login_logo).isVisible)
    }

    private suspend fun lockCollection() {
        throwOnShowError = false
        backend.replaceWith { BackendEmulatingOpenConflict() }
    }

    private suspend fun failAuthentication() =
        backend.replaceWith {
            object : Backend() {
                override fun syncLogin(input: SyncLoginRequest): SyncAuth =
                    throw BackendSyncAuthFailedException(
                        backendError {
                            kind = BackendError.Kind.SYNC_AUTH_ERROR
                            message = "invalid credentials"
                        },
                    )
            }
        }

    private suspend fun succeedAuthentication() =
        backend.replaceWith {
            object : Backend() {
                override fun syncLogin(input: SyncLoginRequest): SyncAuth = syncAuth { hkey = TEST_HKEY }
            }
        }

    private fun startLoginScreen(): ActivityController<AccountActivity> =
        startActivityControllerNormallyOpenCollectionWithIntent(
            AccountActivity::class.java,
            AccountActivity.getIntent(targetContext),
        )

    private fun ActivityController<AccountActivity>.enterCredentials() {
        get().findViewById<TextInputEditText>(R.id.username).setText("user@example.com")
        get().findViewById<TextInputEditField>(R.id.password).setText("hunter2")
    }

    private fun ActivityController<AccountActivity>.logIn() {
        enterCredentials()
        get().findViewById<Button>(R.id.login_button).performClick()
        advanceRobolectricLooper()
    }

    private val AccountActivity.isBlockingTouches: Boolean
        get() = window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0

    private val syncNowMessage: String
        get() = targetContext.getString(CommonString.sync_now)

    companion object {
        private const val TEST_HKEY = "test-hkey"
    }
}
