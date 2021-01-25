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

import com.ichi2.libanki.DB;

import net.ankiweb.rsdroid.BackendFactory;
import net.ankiweb.rsdroid.BackendUtils;

import timber.log.Timber;

/** The Backend in Rust */
public class RustDroidBackend implements DroidBackend {
    // I think we can change this to BackendV1 once new DB() accepts it.
    private final BackendFactory mBackend;


    public RustDroidBackend(BackendFactory mBackend) {
        this.mBackend = mBackend;
    }


    @Override
    public DB openCollectionDatabase(String path) {
        return new DB(path, mBackend);
    }


    @Override
    public void closeCollection() {
        mBackend.closeCollection();
    }


    @Override
    public boolean databaseCreationCreatesSchema() {
        return true;
    }
}
