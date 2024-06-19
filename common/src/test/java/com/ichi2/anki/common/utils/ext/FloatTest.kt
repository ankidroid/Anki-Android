/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.common.utils.ext

import androidx.core.math.MathUtils.clamp
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/**
 * Tests for methods in [Float][clamp]
 */
class FloatTest {

    @Test
    fun clampOneZero() {
        fun Float.clampOneZero() = this.clamp(0f, 1f)

        assertThat(1.0f.clampOneZero(), equalTo(1.0f))
        assertThat(1.1f.clampOneZero(), equalTo(1.0f))

        assertThat(0.0f.clampOneZero(), equalTo(0.0f))
        assertThat((-1.0f).clampOneZero(), equalTo(0.0f))

        assertThat((0.5f).clampOneZero(), equalTo(0.5f))
        assertThat((0.3f).clampOneZero(), equalTo(0.3f))
    }

    @Test
    fun clampCustomValue() {
        fun Float.clampPercentage() = this.clamp(0f, 1000f)

        assertThat(1.0f.clampPercentage(), equalTo(1.0f))
        assertThat(1.1f.clampPercentage(), equalTo(1.1f))
    }
}
