// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("PackageDirectoryMismatch")

package com.google.android.material.snackbar

/*
 * This only exists so that we can call onAttachedToWindow, which is package-private.
 */
fun Snackbar.onAttachedToWindow2() {
    onAttachedToWindow()
}
