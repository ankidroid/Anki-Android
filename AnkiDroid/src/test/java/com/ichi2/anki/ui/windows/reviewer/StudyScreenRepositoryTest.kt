// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.SharedPreferences
import android.content.res.Resources
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.ReviewerMenuRepository
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.settings.PrefsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.test.assertNotSame

class StudyScreenRepositoryTest {
    private val sharedPrefs: SharedPreferences = SPMockBuilder().createSharedPreferences()
    private val menuRepository = ReviewerMenuRepository(sharedPrefs)
    private val prefs: PrefsRepository

    init {
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        prefs = PrefsRepository(sharedPrefs, mockResources)
    }

    @Test
    fun `isMarkShownInToolbar and isFlagShownInToolbar are true when toolbar is enabled and action is set to ALWAYS`() {
        menuRepository.setDisplayTypeActions(
            alwaysShowActions =
                listOf(
                    ViewerAction.MARK,
                    ViewerAction.FLAG_MENU,
                ),
            menuOnlyActions = listOf(),
            disabledActions = listOf(),
        )
        val repository = StudyScreenRepository(prefs)

        assertTrue(repository.isMarkShownInToolbar)
    }

    @Test
    fun `isMarkShownInToolbar and isFlagShownInToolbar are false when action is NOT in ALWAYS list`() {
        menuRepository.setDisplayTypeActions(
            alwaysShowActions = listOf(),
            menuOnlyActions =
                listOf(
                    ViewerAction.MARK,
                    ViewerAction.FLAG_MENU,
                ),
            disabledActions = listOf(),
        )
        val repository = StudyScreenRepository(prefs)

        assertFalse(repository.isMarkShownInToolbar)
    }

    @Test
    fun `isMarkShownInToolbar and isFlagShownInToolbar are false when toolbar is completely hidden`() {
        menuRepository.setDisplayTypeActions(
            alwaysShowActions =
                listOf(
                    ViewerAction.MARK,
                    ViewerAction.FLAG_MENU,
                ),
            menuOnlyActions = listOf(),
            disabledActions = listOf(),
        )
        sharedPrefs.edit {
            putString(R.string.reviewer_toolbar_position_key.toString(), R.string.reviewer_toolbar_value_none.toString())
        }
        val repository = StudyScreenRepository(prefs)

        assertFalse(repository.isMarkShownInToolbar)
    }

    @Test
    fun `getServerPort returns 0 when useFixedPortInReviewer is false`() {
        prefs.useFixedPortInReviewer = false

        val repository = StudyScreenRepository(prefs)
        val port = repository.getServerPort()

        assertEquals(0, port)
    }

    @Test
    fun `getServerPort returns valid port when configured and available`() {
        prefs.useFixedPortInReviewer = true
        prefs.reviewerPort = 0

        val repository = StudyScreenRepository(prefs)
        val port = repository.getServerPort()

        assertTrue(port > 0)
        assertEquals(prefs.reviewerPort, port)
    }

    @Test
    fun `getServerPort returns 0 when specific port is already in use`() {
        val blockerSocket = ServerSocket(0)
        val busyPort = blockerSocket.localPort

        try {
            prefs.useFixedPortInReviewer = true
            prefs.reviewerPort = busyPort

            val repository = StudyScreenRepository(prefs)
            val resultPort = repository.getServerPort()

            assertEquals(0, resultPort)
        } finally {
            blockerSocket.close()
        }
    }

    @Test
    fun `generateStateMutationKey generates unique keys`() =
        runTest {
            val repository = StudyScreenRepository(prefs)
            val first = repository.generateStateMutationKey()
            delay(10)
            val second = repository.generateStateMutationKey()
            assertNotSame(first, second)
        }
}
