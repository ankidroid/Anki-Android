// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.preferences

import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat.Companion.ValidationResult
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat.Companion.validate
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToggleableIncrementerNumberRangePreferenceCompatTest {
    @Test
    fun `validation logic works with toggleable min of 0`() {
        val min = 0
        val max = 999

        assertThat(
            "0 should be VALID (represents disabled state)",
            validate("0", 0, min, max),
            IsEqual(ValidationResult.VALID),
        )

        assertThat(
            "A number within range should be VALID",
            validate("20", 20, min, max),
            IsEqual(ValidationResult.VALID),
        )

        assertThat(
            "Max boundary should be VALID",
            validate("999", 999, min, max),
            IsEqual(ValidationResult.VALID),
        )

        assertThat(
            "A number greater than max should return OVERFLOW",
            validate("1000", 1000, min, max),
            IsEqual(ValidationResult.OVERFLOW),
        )

        assertThat(
            "An empty string should return EMPTY",
            validate("", null, min, max),
            IsEqual(ValidationResult.EMPTY),
        )

        assertThat(
            "A number exceeding Integer limits should return INVALID",
            validate("9999999999", null, min, max),
            IsEqual(ValidationResult.INVALID),
        )
    }

    @Test
    fun `toggle off default value is 1 when min is 0`() {
        val min = 0
        val defaultValue = maxOf(1, min)
        assertThat(
            "Default value when toggling ON from disabled should be 1",
            defaultValue,
            IsEqual(1),
        )
    }

    @Test
    fun `toggle off default value respects min when min is positive`() {
        val min = 5
        val defaultValue = maxOf(1, min)
        assertThat(
            "Default value when toggling ON should be at least min",
            defaultValue,
            IsEqual(5),
        )
    }
}
