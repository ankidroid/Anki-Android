// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.graphics.Bitmap
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class BitmapUtilTest {
    @Test
    fun calculateInSampleSizeRejectsNonPositiveDimensions() {
        assertThat(BitmapUtil.calculateInSampleSize(100, 100, 0, 100), equalTo(1))
        assertThat(BitmapUtil.calculateInSampleSize(100, 100, 100, 0), equalTo(1))
        assertThat(BitmapUtil.calculateInSampleSize(0, 100, 100, 100), equalTo(1))
        assertThat(BitmapUtil.calculateInSampleSize(100, 0, 100, 100), equalTo(1))
        assertThat(BitmapUtil.calculateInSampleSize(-1, 100, 100, 100), equalTo(1))
    }

    @Test
    fun calculateInSampleSizeKeepsImageThatAlreadyFits() {
        assertThat(BitmapUtil.calculateInSampleSize(100, 100, 100, 100), equalTo(1))
        assertThat(BitmapUtil.calculateInSampleSize(50, 80, 100, 100), equalTo(1))
    }

    @Test
    fun calculateInSampleSizeDoesNotShrinkWhenOneDimensionWouldFallBelowRequest() {
        assertThat(BitmapUtil.calculateInSampleSize(400, 100, 100, 100), equalTo(1))
        assertThat(BitmapUtil.calculateInSampleSize(100, 400, 100, 100), equalTo(1))
    }

    @Test
    fun calculateInSampleSizeUsesPowerOfTwoCoveringBothDimensions() {
        assertThat(BitmapUtil.calculateInSampleSize(400, 400, 150, 150), equalTo(2))
        assertThat(BitmapUtil.calculateInSampleSize(400, 400, 100, 100), equalTo(4))
        assertThat(BitmapUtil.calculateInSampleSize(8192, 8192, 1080, 1920), equalTo(4))
    }

    @Test
    fun calculateInSampleSizeDoesNotOverflowOnHugeSources() {
        val sample = BitmapUtil.calculateInSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 1, 1)
        assertThat(sample >= 1, equalTo(true))
        assertThat(sample and (sample - 1), equalTo(0))
    }

    @Test
    fun preferredConfigDependsOnAlpha() {
        assertThat(BitmapUtil.preferredConfig(hasAlpha = true), equalTo(Bitmap.Config.ARGB_8888))
        assertThat(BitmapUtil.preferredConfig(hasAlpha = false), equalTo(Bitmap.Config.RGB_565))
    }
}
