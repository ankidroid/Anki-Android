/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anim

import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ActivityTransitionAnimationTest {
    @ParameterizedTest
    @EnumSource(
        value = Direction::class,
        mode = EnumSource.Mode.EXCLUDE,
        names = ["START", "END", "UP", "DOWN", "RIGHT", "LEFT"],
    )
    fun getInverseTransition_returns_same_input_for_not_directional_params(direction: Direction) {
        assertThat(getInverseTransition(direction), equalTo(direction))
    }

    @ParameterizedTest
    @MethodSource("getInverseTransition_returns_inverse_direction_args")
    fun getInverseTransition_returns_inverse_direction(
        first: Direction,
        second: Direction,
    ) {
        assertThat(getInverseTransition(first), equalTo(second))
        assertThat(getInverseTransition(second), equalTo(first))
    }

    companion object {
        @JvmStatic // used in @MethodSource
        fun getInverseTransition_returns_inverse_direction_args(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(Direction.START, Direction.END),
                Arguments.of(Direction.UP, Direction.DOWN),
                Arguments.of(Direction.RIGHT, Direction.LEFT),
            )
        }
    }
}
