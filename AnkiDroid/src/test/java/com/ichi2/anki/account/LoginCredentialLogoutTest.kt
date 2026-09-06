// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.account

import android.os.Bundle
import android.widget.Button
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.exceptions.ClearCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LoginCredentialLogoutTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    private val credentialManager = mockk<CredentialManager>()

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
    fun `logout does not automatically sign in with the previous credential`() =
        runTest {
            coEvery { credentialManager.clearCredentialState(any()) } returns Unit
            val account = startSignedInAccountScreen()

            account.get().findViewById<Button>(R.id.logout_button).performClick()
            advanceRobolectricLooper()

            assertTrue(Prefs.hkey.isNullOrEmpty(), "explicit logout must leave the account signed out")
            assertIs<LoginFragment>(account.get().supportFragmentManager.findFragmentById(R.id.fragment_container))
            coVerify(exactly = 0) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
            coVerify(exactly = 1) { credentialManager.clearCredentialState(any()) }
        }

    @Test
    fun `logout completes when the credential provider responds in the background`() =
        runTest {
            val providerResponse = CompletableDeferred<Unit>()
            coEvery { credentialManager.clearCredentialState(any()) } coAnswers { providerResponse.await() }
            val account = startSignedInAccountScreen()

            try {
                account.get().findViewById<Button>(R.id.logout_button).performClick()
                advanceRobolectricLooper()
                coVerify(exactly = 1) { credentialManager.clearCredentialState(any()) }
                assertIs<LoginFragment>(account.get().supportFragmentManager.findFragmentById(R.id.fragment_container))
                account.pause().saveInstanceState(Bundle()).stop()
            } finally {
                providerResponse.complete(Unit)
                advanceRobolectricLooper()
            }
            account.restart().start().resume()
            advanceRobolectricLooper()

            assertTrue(Prefs.hkey.isNullOrEmpty())
            assertIs<LoginFragment>(account.get().supportFragmentManager.findFragmentById(R.id.fragment_container))
            coVerify(exactly = 0) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
        }

    @Test
    fun `logout stays signed out when the credential provider cannot clear its state`() =
        runTest {
            coEvery { credentialManager.clearCredentialState(any()) } throws ClearCredentialUnsupportedException()
            val account = startSignedInAccountScreen()

            account.get().findViewById<Button>(R.id.logout_button).performClick()
            advanceRobolectricLooper()

            assertTrue(Prefs.hkey.isNullOrEmpty())
            assertIs<LoginFragment>(account.get().supportFragmentManager.findFragmentById(R.id.fragment_container))
            coVerify(exactly = 0) { credentialManager.getCredential(any(), any<GetCredentialRequest>()) }
        }

    @Test
    fun `reopening the account screen after logout requires choosing a saved credential`() =
        runTest {
            coEvery { credentialManager.clearCredentialState(any()) } throws ClearCredentialUnsupportedException()
            coEvery { credentialManager.getCredential(any(), any<GetCredentialRequest>()) } throws GetCredentialCancellationException()
            val account = startSignedInAccountScreen()
            account.get().findViewById<Button>(R.id.logout_button).performClick()
            advanceRobolectricLooper()
            account.pause().stop().destroy()

            val reopened = startAccountScreen()
            advanceRobolectricLooper()

            coVerify(exactly = 1) {
                credentialManager.getCredential(
                    any(),
                    match<GetCredentialRequest> { !(it.credentialOptions.single() as GetPasswordOption).isAutoSelectAllowed },
                )
            }
            assertTrue(Prefs.hkey.isNullOrEmpty())
            assertIs<LoginFragment>(reopened.get().supportFragmentManager.findFragmentById(R.id.fragment_container))
        }

    private fun startSignedInAccountScreen(): ActivityController<AccountActivity> {
        Prefs.username = "user@example.com"
        Prefs.hkey = TEST_HKEY
        return startAccountScreen()
    }

    private fun startAccountScreen(): ActivityController<AccountActivity> =
        startActivityControllerNormallyOpenCollectionWithIntent(
            AccountActivity::class.java,
            AccountActivity.getIntent(targetContext),
        )

    companion object {
        private const val TEST_HKEY = "test-hkey"
    }
}
