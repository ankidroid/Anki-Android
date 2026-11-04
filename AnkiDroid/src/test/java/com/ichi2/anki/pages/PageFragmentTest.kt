// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 mixelas <michelakisgio@gmail.com>
package com.ichi2.anki.pages

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withFragment
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PageFragmentTest : RobolectricTest() {
    @Test
    fun `UI request received while stopped runs when the page starts`() =
        runTest {
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                val (page, activity) = scenario.withFragment { this to requireActivity() }
                scenario.moveToState(Lifecycle.State.CREATED)

                val request = async { page.handlePostRequest(CLOSE_REQUEST, byteArrayOf()) }
                advanceUntilIdle()
                assertFalse(request.isCompleted, "request should wait for the page to start")
                assertFalse(activity.isFinishing, "a stopped page should not finish")

                scenario.moveToState(Lifecycle.State.RESUMED)
                request.await()

                assertTrue(activity.isFinishing)
            }
        }

    @Test
    fun `UI requests received while stopped are queued without blocking other requests`() =
        runTest {
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                val page = scenario.withFragment { this }
                scenario.moveToState(Lifecycle.State.CREATED)

                val first = async { page.handlePostRequest(CLOSE_REQUEST, byteArrayOf()) }
                val second = async { page.handlePostRequest(CLOSE_REQUEST, byteArrayOf()) }
                advanceUntilIdle()

                page.handlePostRequest(PostRequestUri("/_anki/congratsInfo"), byteArrayOf())
                assertFalse(first.isCompleted, "first request should wait for the page to start")
                assertFalse(second.isCompleted, "second request should wait for the page to start")

                scenario.moveToState(Lifecycle.State.RESUMED)
                first.await()
                second.await()
            }
        }

    @Test
    fun `UI request queued while stopped fails if the page is destroyed`() =
        runTest {
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                val page = scenario.withFragment { this }
                scenario.moveToState(Lifecycle.State.CREATED)

                val request = async { runCatching { page.handlePostRequest(CLOSE_REQUEST, byteArrayOf()) } }
                advanceUntilIdle()
                scenario.moveToState(Lifecycle.State.DESTROYED)

                assertTrue(request.await().isFailure, "a destroyed page should not run a queued request")
            }
        }

    @Test
    fun `background safe UI request runs while stopped`() =
        runTest {
            launchFragmentInContainer<CongratsPage>().use { scenario ->
                val page = scenario.withFragment { this }
                scenario.moveToState(Lifecycle.State.CREATED)

                page.handlePostRequest(PostRequestUri("/_anki/i18nResources"), byteArrayOf())
            }
        }

    companion object {
        private val CLOSE_REQUEST = PostRequestUri("/_anki/importDialogRequireClose")
    }
}
