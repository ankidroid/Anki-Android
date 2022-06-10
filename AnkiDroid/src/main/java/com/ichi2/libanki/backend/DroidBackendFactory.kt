/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki.backend

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiDroidApp
import net.ankiweb.rsdroid.NativeMethods

/** Responsible for selection of legacy or new Rust backend  */
object DroidBackendFactory {
    @JvmStatic
    private var sBackendForTesting: DroidBackend? = null

    @JvmStatic
    fun getInstance(): DroidBackend {
        NativeMethods.ensureSetup()
        return sBackendForTesting ?: if (AnkiDroidApp.TESTING_USE_V16_BACKEND) {
            RustDroidV16Backend(AnkiDroidApp.currentBackendFactory())
        } else {
            RustDroidBackend(AnkiDroidApp.currentBackendFactory())
        }
    }

    @JvmStatic
    @VisibleForTesting
    fun setOverride(backend: DroidBackend?) {
        sBackendForTesting = backend
    }
}
