/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils;

import com.ichi2.libanki.DB;
import com.ichi2.libanki.backend.DroidBackendFactory;
import com.ichi2.libanki.backend.RustDroidBackend;

import net.ankiweb.rsdroid.BackendException;
import net.ankiweb.rsdroid.BackendFactory;
import net.ankiweb.rsdroid.RustBackendFailedException;

import BackendProto.Backend;

import static org.mockito.Mockito.mock;

/** Test helper:
 * causes getCol to emulate an exception caused by having another AnkiDroid instance open on the same collection
 */
public class BackendEmulatingOpenConflict extends RustDroidBackend {

    public BackendEmulatingOpenConflict(BackendFactory backend) {
        super(backend);
    }


    public static void enable() {
        try {
            DroidBackendFactory.setOverride(new BackendEmulatingOpenConflict(BackendFactory.createInstance()));
        } catch (RustBackendFailedException e) {
            throw new RuntimeException(e);
        }
    }


    public static void disable() {
        DroidBackendFactory.setOverride(null);
    }


    @Override
    public DB openCollectionDatabase(String path) {
        Backend.BackendError error = mock(Backend.BackendError.class);
        throw new BackendException.BackendDbException.BackendDbLockedException(error);
    }
}
