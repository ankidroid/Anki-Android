// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.ui

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.junit.Test

class RecyclerFastScrollerTest {
    @Test
    fun `thumb height is the share of the content that fits on screen`() {
        // a quarter of the content is visible, so the thumb is a quarter of the bar
        assertThat(computeThumbHeight(barHeight = 1000, scrollRange = 4000, minHandleHeight = 48), equalTo(250))
    }

    @Test
    fun `thumb height never drops below the minimum`() {
        assertThat(computeThumbHeight(barHeight = 1000, scrollRange = 100_000, minHandleHeight = 48), equalTo(48))
    }

    @Test
    fun `thumb height never exceeds the bar`() {
        assertThat(computeThumbHeight(barHeight = 1000, scrollRange = 1000, minHandleHeight = 48), equalTo(1000))
    }

    @Test
    fun `thumb height stays within the bar even when the minimum is taller than the bar`() {
        assertThat(computeThumbHeight(barHeight = 30, scrollRange = 100_000, minHandleHeight = 48), equalTo(30))
    }

    @Test
    fun `proportion is zero at the top of the list`() {
        assertThat(computeScrollProportion(scrollOffset = 0, scrollRange = 4000, barHeight = 1000), equalTo(0f))
    }

    @Test
    fun `proportion reaches one at the bottom of the list`() {
        // the last screen starts at scrollRange - barHeight
        assertThat(computeScrollProportion(scrollOffset = 3000, scrollRange = 4000, barHeight = 1000), equalTo(1f))
    }

    @Test
    fun `proportion tracks scrolled pixels linearly`() {
        // half the scrollable pixels means the thumb is at the middle: this is what keeps it
        // smooth on uneven rows, since it follows pixels rather than item index
        assertThat(computeScrollProportion(scrollOffset = 1500, scrollRange = 4000, barHeight = 1000).toDouble(), closeTo(0.5, 0.0001))
        assertThat(computeScrollProportion(scrollOffset = 750, scrollRange = 4000, barHeight = 1000).toDouble(), closeTo(0.25, 0.0001))
    }

    @Test
    fun `proportion is clamped into range`() {
        assertThat(computeScrollProportion(scrollOffset = 9999, scrollRange = 4000, barHeight = 1000), equalTo(1f))
    }

    @Test
    fun `proportion does not divide by zero when the list barely scrolls`() {
        // scrollRange == barHeight would be a zero divisor without the guard
        assertThat(computeScrollProportion(scrollOffset = 0, scrollRange = 1000, barHeight = 1000), lessThan(1.0001f))
    }
}
