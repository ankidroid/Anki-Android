/*
 *  Copyright (c) 2026  Galal Ahmed <ga71387@gmail.com>
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

package com.ichi2.anki.utils.ext

import android.app.Activity
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

val Activity.windowInsetsControllerCompat: WindowInsetsControllerCompat
    get() = window.windowInsetsControllerCompat

val Fragment.windowInsetsControllerCompat: WindowInsetsControllerCompat
    get() = window.windowInsetsControllerCompat

val Window.windowInsetsControllerCompat: WindowInsetsControllerCompat
    get() = WindowCompat.getInsetsController(this, decorView)

inline fun Activity.withInsets(block: WindowInsetsControllerCompat.() -> Unit) = windowInsetsControllerCompat.apply(block)

inline fun Fragment.withInsets(block: WindowInsetsControllerCompat.() -> Unit) = windowInsetsControllerCompat.apply(block)

inline fun Window.withInsets(block: WindowInsetsControllerCompat.() -> Unit) = windowInsetsControllerCompat.apply(block)
