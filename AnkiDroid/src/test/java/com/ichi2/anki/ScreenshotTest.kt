/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import org.junit.experimental.categories.Category
import org.robolectric.annotation.GraphicsMode

interface ScreenshotTestCategory

/**
 * Base class for [roborazzi](https://github.com/takahirom/roborazzi) screenshot tests
 */
@Category(ScreenshotTestCategory::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest : RobolectricTest()
