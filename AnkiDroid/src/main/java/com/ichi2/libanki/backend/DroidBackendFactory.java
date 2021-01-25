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

package com.ichi2.libanki.backend;

import com.ichi2.anki.AnkiDroidApp;

import net.ankiweb.rsdroid.BackendFactory;
import net.ankiweb.rsdroid.RustBackendFailedException;

import androidx.annotation.Nullable;
import timber.log.Timber;

/** Responsible for selection of either the Rust or Java-based backend */
public class DroidBackendFactory {

    /** Intentionally private - use {@link DroidBackendFactory#getInstance(boolean)}} */
    private DroidBackendFactory() {

    }

    /**
     * Obtains an instance of a {@link DroidBackend}.
     * Each call to this method will generate a separate instance which can handle a new Anki collection
     */
    public static DroidBackend getInstance(boolean useBackend) {

        BackendFactory backendFactory = null;
        if (useBackend) {
            try {
                backendFactory = BackendFactory.createInstance();
            } catch (RustBackendFailedException e) {
                Timber.w(e, "Rust backend failed to load - falling back to Java");
                AnkiDroidApp.sendExceptionReport(e, "DroidBackendFactory::getInstance");
            }
        }
        return getInstance(backendFactory);
    }

    private static DroidBackend getInstance(@Nullable BackendFactory backendFactory) {
        if (backendFactory == null) {
            return new JavaDroidBackend();
        } else {
            return new RustDroidBackend(backendFactory);
        }
    }
}
