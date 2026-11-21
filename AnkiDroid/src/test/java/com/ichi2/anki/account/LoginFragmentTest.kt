// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.account

import android.app.Activity.RESULT_OK
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.autofill.AutofillValue
import android.widget.Button
import androidx.core.view.isVisible
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.sync.SyncAuth
import anki.sync.SyncLoginRequest
import anki.sync.syncAuth
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.findAnkiActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.testutils.skipTest
import com.ichi2.testutils.withQueuedCollectionAccess
import com.ichi2.ui.TextInputEditField
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.sun.net.httpserver.HttpServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncAuthFailedException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
class LoginFragmentTest : RobolectricTest() {
    @get:Rule
    val backend = BackendOverrideRule()

    // Robolectric has no credential provider, so its requests never complete
    private val credentialManager =
        mockk<CredentialManager> {
            coEvery { getCredential(any(), any<GetCredentialRequest>()) } throws NoCredentialException()
            coEvery { createCredential(any(), any()) } throws CreateCredentialCancellationException()
        }

    @Before
    fun mockCredentialManager() {
        mockkObject(CredentialManager.Companion)
        every { CredentialManager.create(any()) } returns credentialManager
    }

    @After
    fun unmockCredentialManager() {
        unmockkObject(CredentialManager.Companion)
    }

    @Test
    fun `custom sync does not automatically submit a saved AnkiWeb password`() =
        runTest {
            editPreferences {
                putBoolean("syncBaseUrl_switch", true)
                putString("syncBaseUrl", "https://custom-sync.example/")
            }
            val requests = mutableListOf<SyncLoginRequest>()
            backend.replaceWith {
                object : Backend() {
                    override fun syncLogin(input: SyncLoginRequest): SyncAuth {
                        requests += input
                        return syncAuth { hkey = TEST_HKEY }
                    }
                }
            }
            coEvery {
                credentialManager.getCredential(any(), any<GetCredentialRequest>())
            } returns GetCredentialResponse(PasswordCredential("AnkiWeb: user@example.com", "ankiweb-password"))

            val activity = startLoginScreen().get()
            advanceRobolectricLooper()

            assertTrue(requests.isEmpty(), "opening custom sync login must not send an AnkiWeb password to another server")
            if (SDK_INT >= O) {
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, activity.findViewById<View>(R.id.username).importantForAutofill)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, activity.findViewById<View>(R.id.password).importantForAutofill)
            }
        }

    @Test
    fun `endpoint override does not request saved AnkiWeb passwords`() =
        runTest {
            Prefs.currentSyncUri = "https://custom-sync.example/"

            startLoginScreen()
            advanceRobolectricLooper()

            coVerify(exactly = 0) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
        }

    @Test
    fun `an expired session restored with a regional redirect offers saved AnkiWeb passwords`() =
        runTest {
            if (SDK_INT < O) skipTest("Autofill requires API 26")
            Prefs.username = ""
            Prefs.hkey = ""
            Prefs.currentSyncUri = "https://sync19.ankiweb.net/"
            succeedAuthentication { assertEquals("https://sync19.ankiweb.net/", it.endpoint) }
            coEvery {
                credentialManager.getCredential(any(), any<GetCredentialRequest>())
            } returns GetCredentialResponse(PasswordCredential("AnkiWeb: user@example.com", "saved-password"))

            val activity = startLoginScreen().get()
            advanceRobolectricLooper()

            assertTrue(activity.findViewById<View>(R.id.password).isImportantForAutofill)
            coVerify(exactly = 1) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
            assertEquals(TEST_HKEY, Prefs.hkey)
        }

    @Test
    fun `login through an endpoint override does not save a password for AnkiWeb`() =
        runTest {
            Prefs.currentSyncUri = "https://custom-sync.example/"
            succeedAuthentication()

            startLoginScreen().logIn()

            assertEquals(TEST_HKEY, Prefs.hkey)
            coVerify(exactly = 0) { credentialManager.createCredential(any(), any()) }
        }

    @Test
    fun `untagged autofill password is not submitted to AnkiWeb`() =
        runTest {
            succeedAuthentication()
            coEvery {
                credentialManager.getCredential(any(), any<GetCredentialRequest>())
            } returns GetCredentialResponse(PasswordCredential("user@example.com", "custom-server-password"))

            startLoginScreen()
            advanceRobolectricLooper()

            assertTrue(Prefs.hkey.isNullOrEmpty())
        }

    @Test
    fun `legacy autofill fills the login form without submitting it`() =
        runTest {
            if (SDK_INT < O) skipTest("Autofill requires API 26")

            succeedAuthentication()
            val activity = startLoginScreen().get()
            val password = activity.findViewById<TextInputEditField>(R.id.password)

            activity.findViewById<TextInputEditText>(R.id.username).setText("user@example.com")
            password.autofill(AutofillValue.forText("custom-server-password"))
            advanceRobolectricLooper()

            assertEquals("custom-server-password", password.text.toString())
            assertTrue(Prefs.hkey.isNullOrEmpty())
        }

    @Test
    fun `saved password is discarded if the endpoint changes while it is being retrieved`() =
        runTest {
            succeedAuthentication()
            coEvery {
                credentialManager.getCredential(any(), any<GetCredentialRequest>())
            } coAnswers {
                Prefs.currentSyncUri = "https://custom-sync.example/"
                GetCredentialResponse(PasswordCredential("AnkiWeb: user@example.com", "hunter2"))
            }

            val activity = startLoginScreen().get()
            advanceRobolectricLooper()

            assertTrue(Prefs.hkey.isNullOrEmpty())
            assertTrue(activity.findViewById<TextInputEditField>(R.id.password).text.isNullOrEmpty())
        }

    @Test
    fun `custom sync login does not overwrite an AnkiWeb password`() =
        runTest {
            editPreferences {
                putBoolean("syncBaseUrl_switch", true)
                putString("syncBaseUrl", "https://custom-sync.example/")
            }
            succeedAuthentication()

            startLoginScreen().logIn()

            assertEquals(TEST_HKEY, Prefs.hkey)
            coVerify(exactly = 0) { credentialManager.createCredential(any(), any()) }
            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `successful login offers to save the credential`() =
        runTest {
            succeedAuthentication()

            startLoginScreen().logIn()

            coVerify {
                credentialManager.createCredential(
                    any(),
                    match<CreatePasswordRequest> { it.id == "AnkiWeb: user@example.com" && it.password == "hunter2" },
                )
            }
            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `correcting an autofilled password offers to update the saved credential`() =
        runTest {
            if (SDK_INT < O) skipTest("Autofill requires API 26")
            succeedAuthentication()
            val activity = startLoginScreen().get()
            val username = activity.findViewById<TextInputEditText>(R.id.username)
            val password = activity.findViewById<TextInputEditField>(R.id.password)
            username.autofill(AutofillValue.forText("AnkiWeb: user@example.com"))
            password.autofill(AutofillValue.forText("old-password"))

            password.setText("new-password")
            activity.findViewById<Button>(R.id.login_button).performClick()
            advanceRobolectricLooper()

            assertEquals(TEST_HKEY, Prefs.hkey)
            coVerify(exactly = 1) {
                credentialManager.createCredential(
                    any(),
                    match<CreatePasswordRequest> { it.id == "AnkiWeb: user@example.com" && it.password == "new-password" },
                )
            }
        }

    @Test
    fun `correcting an autofilled email offers to save the new account ID`() =
        runTest {
            if (SDK_INT < O) skipTest("Autofill requires API 26")
            succeedAuthentication { assertEquals("new@example.com", it.username) }
            val activity = startLoginScreen().get()
            val username = activity.findViewById<TextInputEditText>(R.id.username)
            val password = activity.findViewById<TextInputEditField>(R.id.password)
            username.autofill(AutofillValue.forText("AnkiWeb: old@example.com"))
            password.autofill(AutofillValue.forText("saved-password"))

            username.setText("AnkiWeb: new@example.com")
            activity.findViewById<Button>(R.id.login_button).performClick()
            advanceRobolectricLooper()

            assertEquals(TEST_HKEY, Prefs.hkey)
            coVerify(exactly = 1) {
                credentialManager.createCredential(
                    any(),
                    match<CreatePasswordRequest> { it.id == "AnkiWeb: new@example.com" && it.password == "saved-password" },
                )
            }
        }

    @Test
    fun `an unchanged autofilled password is not offered for saving again after recreation`() =
        runTest {
            if (SDK_INT < O) skipTest("Autofill requires API 26")
            succeedAuthentication()
            val login = startLoginScreen()
            login.get().findViewById<TextInputEditText>(R.id.username).autofill(AutofillValue.forText("AnkiWeb: user@example.com"))
            login.get().findViewById<TextInputEditField>(R.id.password).autofill(AutofillValue.forText("saved-password"))

            login.recreate()
            login.get().findViewById<Button>(R.id.login_button).performClick()
            advanceRobolectricLooper()

            assertEquals(TEST_HKEY, Prefs.hkey)
            coVerify(exactly = 0) { credentialManager.createCredential(any(), any()) }
        }

    @Test
    fun `recreation after the save prompt does not offer to save again`() =
        runTest {
            succeedAuthentication()
            val login = startLoginScreen()
            login.logIn()
            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))

            login.recreate()
            advanceRobolectricLooper()

            coVerify(exactly = 1) { credentialManager.createCredential(any(), any()) }
            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `saved credential logs in without offering to save it again`() =
        runTest {
            succeedAuthentication()
            coEvery {
                credentialManager.getCredential(any(), any<GetCredentialRequest>())
            } returns GetCredentialResponse(PasswordCredential("AnkiWeb: user@example.com", "hunter2"))

            startLoginScreen()
            advanceRobolectricLooper()

            assertEquals(TEST_HKEY, Prefs.hkey)
            assertEquals("user@example.com", Prefs.username)
            coVerify(exactly = 0) { credentialManager.createCredential(any(), any()) }
            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `app scoped saved passwords require the user to choose a credential`() =
        runTest {
            startLoginScreen()
            advanceRobolectricLooper()

            coVerify(exactly = 1) {
                credentialManager.getCredential(
                    any(),
                    match<GetCredentialRequest> { !(it.credentialOptions.single() as GetPasswordOption).isAutoSelectAllowed },
                )
            }
        }

    @Test
    fun `recreation relaunches a credential picker that was still pending`() =
        runTest {
            succeedAuthentication()
            var requests = 0
            coEvery { credentialManager.getCredential(any(), any<GetCredentialRequest>()) } coAnswers {
                if (++requests == 1) awaitCancellation()
                GetCredentialResponse(PasswordCredential("AnkiWeb: user@example.com", "saved-password"))
            }
            val login = startLoginScreen(forResult = true)
            coVerify(exactly = 1) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }

            login.recreate()
            advanceRobolectricLooper()

            coVerify(exactly = 2) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
            assertEquals(TEST_HKEY, Prefs.hkey)
            assertEquals(RESULT_OK, shadowOf(login.get()).resultCode)
        }

    @Test
    fun `recreation does not reopen a credential picker that the user dismissed`() =
        runTest {
            coEvery {
                credentialManager.getCredential(any(), any<GetCredentialRequest>())
            } throws GetCredentialCancellationException()
            val login = startLoginScreen()
            advanceRobolectricLooper()

            login.recreate()
            advanceRobolectricLooper()

            coVerify(exactly = 1) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
        }

    @Test
    fun `manual sign-in supersedes an unfinished password lookup even when authentication fails`() =
        runTest {
            failAuthentication()
            val lookupCancelled = CompletableDeferred<Unit>()
            coEvery { credentialManager.getCredential(any(), any<GetCredentialRequest>()) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    lookupCancelled.complete(Unit)
                }
            }
            val login = startLoginScreen()
            coVerify(exactly = 1) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }

            login.logIn()

            assertEquals("invalid credentials", login.get().snackbarText)
            assertTrue(lookupCancelled.isCompleted, "a manual submission must cancel the earlier credential lookup")

            login.recreate()
            advanceRobolectricLooper()

            coVerify(exactly = 1) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
        }

    @Test
    fun `a saved password stays out of a form whose password visibility was enabled`() =
        runTest {
            failAuthentication()
            val selected = CompletableDeferred<GetCredentialResponse>()
            coEvery { credentialManager.getCredential(any(), any<GetCredentialRequest>()) } coAnswers { selected.await() }
            val activity = startLoginScreen().get()
            val password = activity.findViewById<TextInputEditField>(R.id.password)
            val passwordLayout = activity.findViewById<View>(R.id.password_layout)
            val endIcon = passwordLayout.findViewById<View>(com.google.android.material.R.id.text_input_end_icon)
            endIcon.performClick()
            assertNull(password.transformationMethod)

            selected.complete(GetCredentialResponse(PasswordCredential("AnkiWeb: user@example.com", "saved-password")))
            advanceRobolectricLooper()

            assertEquals("invalid credentials", activity.snackbarText)
            assertTrue(password.text.isNullOrEmpty())
        }

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
    fun `locked collection guidance survives activity recreation`() =
        runTest {
            lockCollection()
            val login = startLoginScreen()
            login.logIn()
            assertContains(getAlertDialogText(checkDismissed = true).orEmpty(), "Advanced settings")

            login.recreate()
            advanceRobolectricLooper()

            // ShadowDialog retains the old dialog, so checking only its text can falsely pass.
            val dialog = assertNotNull(ShadowDialog.getLatestDialog())
            assertSame(login.get(), dialog.context.findAnkiActivity(), "the guidance must be displayed by the recreated activity")
            assertTrue(dialog.isShowing)
            assertContains(getAlertDialogText(checkDismissed = true).orEmpty(), "Advanced settings")
        }

    @Test
    fun `dismissed login error is not shown again after recreation`() =
        runTest {
            lockCollection()
            val login = startLoginScreen()
            login.logIn()
            clickAlertDialogButton { positiveButton }
            val dismissed = ShadowDialog.getLatestDialog()

            login.recreate()
            advanceRobolectricLooper()

            assertSame(dismissed, ShadowDialog.getLatestDialog())
        }

    @Test
    fun `login error still being displayed survives recreation`() =
        runTest {
            failAuthentication()
            val login = startLoginScreen()
            login.enterCredentials()
            login.get().findViewById<Button>(R.id.login_button).performClick()
            // less than the snackbar's duration
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            assertEquals("invalid credentials", login.get().snackbarText)

            login.recreate()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

            assertEquals("invalid credentials", login.get().snackbarText, "recreation must not discard a login error before it can be read")
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
    fun `successful login while stopped shows the success dialog on return`() =
        runTest {
            succeedAuthentication()
            val login = startLoginScreen()

            withQueuedCollectionAccess {
                login.logIn()
                login.pause().stop()
                advanceUntilIdle()
            }
            advanceRobolectricLooper()
            assertEquals(TEST_HKEY, Prefs.hkey)

            login.start().resume()
            advanceRobolectricLooper()

            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `continue after repeated enter submissions leaves no success dialog`() =
        runTest {
            succeedAuthentication()
            val login = startLoginScreen()
            login.enterCredentials()
            login.get().findViewById<TextInputEditField>(R.id.password).requestFocus()

            repeat(2) {
                login.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                login.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            advanceRobolectricLooper()
            assertEquals(TEST_HKEY, Prefs.hkey)
            assertEquals(syncNowMessage, getAlertDialogText(checkDismissed = true))

            clickAlertDialogButton { negativeButton }
            advanceRobolectricLooper()

            assertIs<LoggedInFragment>(login.get().supportFragmentManager.findFragmentById(R.id.fragment_container))
            assertTrue(ShadowDialog.getShownDialogs().none { it.isShowing }, "Continue must not leave a success dialog")
        }

    @Test
    fun `cancelling login interrupts a server that has not responded`() {
        val requestReceived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestReceived.countDown()
            try {
                check(releaseResponse.await(15, TimeUnit.SECONDS))
                exchange.sendResponseHeaders(500, -1)
            } finally {
                exchange.close()
            }
        }
        server.start()
        Prefs.currentSyncUri = "http://127.0.0.1:${server.address.port}/"
        val login = startLoginScreen()
        val activity = login.get()
        login.enterCredentials()
        val backend = CollectionManager.getBackend()
        // the login runs on a real thread, so it waits on the server without blocking the test
        val previousQueue = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            activity.findViewById<Button>(R.id.login_button).performClick()
            assertTrue(requestReceived.await(5, TimeUnit.SECONDS), "the real backend must reach the local server")
            advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }

            ShadowDialog.getLatestDialog().cancel()
            val cancelledAt = TimeSource.Monotonic.markNow()
            while (activity.isBlockingTouches && cancelledAt.elapsedNow() < 3.seconds) {
                Thread.sleep(10)
                advanceRobolectricLooper()
            }

            assertFalse(activity.isBlockingTouches, "cancellation must unblock input without waiting for the server response")
            assertEquals("", Prefs.hkey.orEmpty())
        } finally {
            backend.abortSync()
            releaseResponse.countDown()
            advanceRobolectricLooperUntil { !activity.isBlockingTouches }
            CollectionManager.setTestDispatcher(previousQueue)
            server.stop(0)
        }
    }

    @Test
    fun `back works after cancelling a login waiting for the collection`() =
        runTest {
            succeedAuthentication()
            val login = startLoginScreen()
            login.enterCredentials()
            // never advanced: the login keeps waiting for the collection
            val collectionScheduler = TestCoroutineScheduler()
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(collectionScheduler), useReentrantLock = false)
            try {
                login.get().findViewById<Button>(R.id.login_button).performClick()
                advanceTimeBy(601)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(601))
                val progress = assertNotNull(ShadowDialog.getLatestDialog())
                assertTrue(progress.isShowing)

                progress.cancel()
                shadowOf(Looper.getMainLooper()).idle()
                runCurrent()
                login.get().onBackPressedDispatcher.onBackPressed()

                assertTrue(login.get().isFinishing, "cancelling must unblock Back without waiting for the collection")
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
                collectionScheduler.runCurrent()
            }
            assertTrue(Prefs.hkey.isNullOrEmpty(), "a cancelled login must not log in")
        }

    @Test
    fun `successful retry does not wait for the previous error to time out`() =
        runTest {
            var attempts = 0
            backend.replaceWith {
                object : Backend() {
                    override fun syncLogin(input: SyncLoginRequest): SyncAuth {
                        if (++attempts == 1) throw authFailure()
                        return syncAuth { hkey = TEST_HKEY }
                    }
                }
            }
            // Android's 'Time to take action' setting can keep a snackbar visible for minutes
            shadowOf(targetContext.getSystemService(AccessibilityManager::class.java)).apply {
                setInteractiveUiTimeout(120_000)
                setNonInteractiveUiTimeout(120_000)
            }
            val login = startLoginScreen(forResult = true)
            login.enterCredentials()
            login.get().findViewById<Button>(R.id.login_button).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            assertEquals("invalid credentials", login.get().snackbarText)

            login.get().findViewById<Button>(R.id.login_button).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

            assertEquals(2, attempts)
            assertEquals(TEST_HKEY, Prefs.hkey)
            assertTrue(login.get().isFinishing, "a successful retry must not wait for the previous error to time out")
            assertEquals(RESULT_OK, shadowOf(login.get()).resultCode)
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
                override fun syncLogin(input: SyncLoginRequest): SyncAuth = throw authFailure()
            }
        }

    private fun authFailure() =
        BackendSyncAuthFailedException(
            backendError {
                kind = BackendError.Kind.SYNC_AUTH_ERROR
                message = "invalid credentials"
            },
        )

    private suspend fun succeedAuthentication(onLogin: (SyncLoginRequest) -> Unit = {}) =
        backend.replaceWith {
            object : Backend() {
                override fun syncLogin(input: SyncLoginRequest): SyncAuth {
                    onLogin(input)
                    return syncAuth { hkey = TEST_HKEY }
                }
            }
        }

    private fun startLoginScreen(forResult: Boolean = false): ActivityController<AccountActivity> =
        startActivityControllerNormallyOpenCollectionWithIntent(
            AccountActivity::class.java,
            AccountActivity.getIntent(targetContext, forResult),
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
