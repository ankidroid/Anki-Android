/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewreminders

import com.ichi2.anki.reviewreminders.TroubleshootingCheck.NotificationPermission
import com.ichi2.testutils.TestException
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class ReminderTroubleshootingViewModelTest {
    @Test
    fun `notification permission passed when granted`() {
        val repo =
            mockk<ReminderTroubleshootingRepository> {
                every { isNotificationPermissionGranted() } returns true
            }
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `notification permission failed when not granted`() {
        val repo =
            mockk<ReminderTroubleshootingRepository> {
                every { isNotificationPermissionGranted() } returns false
            }
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Failed))
    }

    @Test
    fun `notification permission error on exception`() {
        val repo =
            mockk<ReminderTroubleshootingRepository> {
                every { isNotificationPermissionGranted() } throws TestException("test")
            }
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult is CheckResult.Error, equalTo(true))
    }

    @Test
    fun `refreshChecks updates state`() {
        val repo =
            mockk<ReminderTroubleshootingRepository> {
                every { isNotificationPermissionGranted() } returns false
            }
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Failed))

        every { repo.isNotificationPermissionGranted() } returns true
        viewModel.refreshChecks()

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `Loading hasIssue is false`() = assertThat(CheckResult.Loading.hasIssue, equalTo(false))

    @Test
    fun `Passed hasIssue is false`() = assertThat(CheckResult.Passed.hasIssue, equalTo(false))

    @Test
    fun `Failed hasIssue is true`() = assertThat(CheckResult.Failed.hasIssue, equalTo(true))

    @Test
    fun `Warning hasIssue is true`() = assertThat(CheckResult.Warning.hasIssue, equalTo(true))

    @Test
    fun `Unavailable hasIssue is false`() = assertThat(CheckResult.Unavailable.hasIssue, equalTo(false))

    @Test
    fun `Error hasIssue is true`() = assertThat(CheckResult.Error(TestException("")).hasIssue, equalTo(true))

    // The `when` below will fail to compile if a new TroubleshootingCheck is added,
    // reminding you to add it to this test and to ReminderTroubleshootingState.checks
    @Test
    fun `checks contains all checks`() =
        withViewModel {
            var count = 0
            for (check in state.value.checks) {
                when (check) {
                    is NotificationPermission -> count++
                }
            }
            assertThat(count, equalTo(1))
        }

    private fun withViewModel(
        notificationPermission: Boolean = true,
        block: ReminderTroubleshootingViewModel.() -> Unit,
    ) {
        val repo =
            mockk<ReminderTroubleshootingRepository> {
                every { isNotificationPermissionGranted() } returns notificationPermission
            }
        ReminderTroubleshootingViewModel(repo).block()
    }
}

private val ReminderTroubleshootingViewModel.notificationPermissionResult: CheckResult
    get() = state.value.notificationPermission.result
