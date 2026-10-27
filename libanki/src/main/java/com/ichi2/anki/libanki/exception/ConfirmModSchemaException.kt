// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.libanki.exception

import timber.log.Timber
import java.lang.Exception

class ConfirmModSchemaException : Exception() {
    /**
     * Add the current exception to log.
     */
    fun log() {
        Timber.v(this)
    }
}
