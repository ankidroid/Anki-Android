// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.utils.CollectionPreferences
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(AndroidJUnit4::class)
class LearnAheadSettingsViewModelTest : RobolectricTest() {
    @Test
    fun `initializes disabled when learn ahead limit is zero`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            val state = viewModel.uiState.value
            assertThat("Should not be loading", state.isLoading, equalTo(false))
            assertThat("Should be disabled", state.isEnabled, equalTo(false))
            assertThat(
                "Default limit should be 20",
                state.limit.minutes,
                equalTo(LearnAheadLimit.DEFAULT_LEARN_AHEAD_MINUTES),
            )
        }

    @Test
    fun `initializes enabled when learn ahead limit is greater than zero`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            val state = viewModel.uiState.value
            assertThat("Should be enabled", state.isEnabled, equalTo(true))
            assertThat("Minutes should match configured value", state.limit.minutes, equalTo(15))
        }

    @Test
    fun `toggling enabled on saves minutes to collection`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.toggleEnabled(true)
            advanceRobolectricLooper()

            assertThat("UI state should be enabled", viewModel.uiState.value.isEnabled, equalTo(true))
            val savedMinutes = CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES)
            assertThat("Collection limit should be 20 minutes", savedMinutes, equalTo(20))
        }

    @Test
    fun `toggling enabled off sets collection limit to zero`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.toggleEnabled(false)
            advanceRobolectricLooper()

            assertThat("UI state should be disabled", viewModel.uiState.value.isEnabled, equalTo(false))
            val savedMinutes = CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES)
            assertThat("Collection limit should be 0", savedMinutes, equalTo(0))
        }

    @Test
    fun `re-enabling after reopening the screen restores the last limit`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(25.toDuration(DurationUnit.MINUTES))

            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.toggleEnabled(false)
            advanceRobolectricLooper()

            val reopened = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            reopened.toggleEnabled(true)
            advanceRobolectricLooper()

            assertThat(
                CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES),
                equalTo(25),
            )
        }

    @Test
    fun `ignores out-of-range input below 1 and above 999`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(10.toDuration(DurationUnit.MINUTES))

            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            viewModel.setMinutes(0)
            assertThat(viewModel.uiState.value.limit.minutes, equalTo(10))

            viewModel.setMinutes(1000)
            assertThat(viewModel.uiState.value.limit.minutes, equalTo(10))
        }

    @Test
    fun `restores customized limit on toggle re-enable`() =
        runTest {
            CollectionPreferences.setLearnAheadLimit(25.toDuration(DurationUnit.MINUTES))

            val viewModel = LearnAheadSettingsViewModel()
            advanceRobolectricLooper()

            assertThat(viewModel.uiState.value.limit.minutes, equalTo(25))

            viewModel.toggleEnabled(false)
            advanceRobolectricLooper()
            assertThat(
                "Collection should be 0 when disabled",
                CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES),
                equalTo(0),
            )

            viewModel.toggleEnabled(true)
            advanceRobolectricLooper()
            assertThat(
                "UI state should restore 25 minutes",
                viewModel.uiState.value.limit.minutes,
                equalTo(25),
            )
            assertThat(
                "Collection should restore 25 minutes",
                CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES),
                equalTo(25),
            )
        }

    @Test
    fun `LearnAheadLimit validation and conversions`() {
        assertThat(LearnAheadLimit.fromMinutes(0), nullValue())
        assertThat(LearnAheadLimit.fromMinutes(1000), nullValue())
        assertThat(LearnAheadLimit.fromMinutes(1)?.minutes, equalTo(1))
        assertThat(LearnAheadLimit.fromMinutes(999)?.minutes, equalTo(999))
        assertThat(LearnAheadLimit.fromMinutes(20)?.toDuration(), equalTo(20.toDuration(DurationUnit.MINUTES)))
        assertThat(0.toDuration(DurationUnit.MINUTES).toLearnAheadLimit(), nullValue())
        assertThat(15.toDuration(DurationUnit.MINUTES).toLearnAheadLimit()?.minutes, equalTo(15))
    }
}
