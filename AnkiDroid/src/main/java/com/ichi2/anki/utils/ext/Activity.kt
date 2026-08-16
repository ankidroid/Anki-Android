// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Galal Ahmed <galal.ahmed8682@gmail.com>

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
