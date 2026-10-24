// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(AndroidJUnit4::class)
class TimeboxSettingsViewModelTest : RobolectricTest() {
    @Test
    fun `TimeboxUiState defaults to loading`() {
        val defaultState = TimeboxUiState()
        assertThat("Should default to loading", defaultState.isLoading, equalTo(true))
    }

    @Test
    fun `initializes disabled when time limit is zero`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            val state = viewModel.uiState.value
            assertThat("Should not be loading", state.isLoading, equalTo(false))
            assertThat("Should be disabled", state.isEnabled, equalTo(false))
            assertThat(
                "Default minutes should be 20",
                state.minutes,
                equalTo(TimeboxSettingsViewModel.DEFAULT_TIMEBOX_MINUTES),
            )
        }

    @Test
    fun `initializes enabled when time limit is greater than zero`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            val state = viewModel.uiState.value
            assertThat("Should be enabled", state.isEnabled, equalTo(true))
            assertThat("Minutes should match configured value", state.minutes, equalTo(15))
        }

    @Test
    fun `toggling enabled on saves minutes to collection`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.toggleEnabled(true)
            advanceRobolectricLooper()

            assertThat("UI state should be enabled", viewModel.uiState.value.isEnabled, equalTo(true))
            val savedMinutes = CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES)
            assertThat("Collection limit should be 20 minutes", savedMinutes, equalTo(20))
        }

    @Test
    fun `toggling enabled off sets collection limit to zero`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.toggleEnabled(false)
            advanceRobolectricLooper()

            assertThat("UI state should be disabled", viewModel.uiState.value.isEnabled, equalTo(false))
            val savedMinutes = CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES)
            assertThat("Collection limit should be 0", savedMinutes, equalTo(0))
        }

    @Test
    fun `increment and decrement updates state and saves when enabled`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(10.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.increment()
            advanceRobolectricLooper()
            assertThat(viewModel.uiState.value.minutes, equalTo(11))
            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(11))

            viewModel.decrement()
            advanceRobolectricLooper()
            assertThat(viewModel.uiState.value.minutes, equalTo(10))
            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(10))
        }

    @Test
    fun `clamping enforces bounds between 1 and 999`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(10.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.setMinutes(0)
            assertThat(viewModel.uiState.value.minutes, equalTo(TimeboxSettingsViewModel.MIN_TIMEBOX_MINUTES))

            viewModel.setMinutes(1000)
            assertThat(viewModel.uiState.value.minutes, equalTo(TimeboxSettingsViewModel.MAX_TIMEBOX_MINUTES))
        }
}
