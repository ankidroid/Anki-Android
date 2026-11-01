// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
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
                state.limit.minutes,
                equalTo(TimeboxTimeLimit.DEFAULT_TIMEBOX_MINUTES),
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
            assertThat("Minutes should match configured value", state.limit.minutes, equalTo(15))
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
            assertThat(viewModel.uiState.value.limit.minutes, equalTo(11))
            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(11))

            viewModel.decrement()
            advanceRobolectricLooper()
            assertThat(viewModel.uiState.value.limit.minutes, equalTo(10))
            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(10))
        }

    @Test
    fun `setMinutes rejects out-of-range values`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(10.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.setMinutes(0)
            assertThat("Below minimum should be rejected", viewModel.uiState.value.limit.minutes, equalTo(10))

            viewModel.setMinutes(10000)
            assertThat("Above maximum should be rejected", viewModel.uiState.value.limit.minutes, equalTo(10))

            viewModel.setMinutes(9999)
            assertThat("Valid upper bound should be accepted", viewModel.uiState.value.limit.minutes, equalTo(9999))
        }

    @Test
    fun `initializes with persisted custom limit when disabled`() =
        runTest {
            Prefs.customTimeboxLimit = 45
            CollectionPreferences.setTimeboxTimeLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            val state = viewModel.uiState.value
            assertThat("Should be disabled", state.isEnabled, equalTo(false))
            assertThat("Should restore persisted minutes", state.limit.minutes, equalTo(45))
        }

    @Test
    fun `setting custom minutes persists to prefs and restores on toggle re-enable`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.setMinutes(45)
            advanceRobolectricLooper()

            assertThat("Prefs should persist customized limit", Prefs.customTimeboxLimit, equalTo(45))

            viewModel.toggleEnabled(false)
            advanceRobolectricLooper()

            assertThat(
                "Collection limit should be zero when disabled",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(0),
            )
            assertThat("UI state should retain minutes when disabled", viewModel.uiState.value.limit.minutes, equalTo(45))

            viewModel.toggleEnabled(true)
            advanceRobolectricLooper()

            assertThat(
                "Collection limit should restore custom minutes on re-enable",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(45),
            )
        }

    @Test
    fun `custom limit is preserved across viewModel re-initialization while disabled`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(35.toDuration(DurationUnit.MINUTES))

            val firstViewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            firstViewModel.toggleEnabled(false)
            advanceRobolectricLooper()
            assertThat(
                "Collection limit should be zero",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(0),
            )

            val secondViewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            assertThat("New ViewModel should be disabled", secondViewModel.uiState.value.isEnabled, equalTo(false))
            assertThat("New ViewModel should restore 35 minutes", secondViewModel.uiState.value.limit.minutes, equalTo(35))

            secondViewModel.toggleEnabled(true)
            advanceRobolectricLooper()
            assertThat(
                "Collection limit should restore 35 minutes",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(35),
            )
        }
}
