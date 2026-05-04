/*
 *  Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
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
package com.ichi2.anki.common.utils.android

import android.os.Environment

/**
 * Utilities for accessing an SD Card (if the user's device supports one).
 *
 * The AnkiDroid collection folder can be stored on an external SD card. This was common
 * in older versions of Android, and is still supported.
 */
object SdCard {
    /**
     * Whether the device's primary external storage (the "SD card") is currently mounted
     * and writable.
     *
     * Returns false when storage is unmounted, removed, read-only, or in any other state
     * that prevents writes (see [Environment.getExternalStorageState]).
     */
    val isMounted: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
}
