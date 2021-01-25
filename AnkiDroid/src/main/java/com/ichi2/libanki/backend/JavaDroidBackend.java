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

import net.ankiweb.rsdroid.RustCleanup;

/**
 * A class which implements the Rust backend functionality in Java - this is to allow moving our current Java code to
 * the rust-based interface so we are able to perform regression testing against the converted interface
 *
 * This also allows an easy switch of functionality once we are happy that there are no regressions
 */
@RustCleanup("After the rust conversion is complete - this will be removed")
public class JavaDroidBackend implements DroidBackend {
    @Override
    public DB openCollectionDatabase(String path) {
        return new DB(path);
    }


    @Override
    public void closeCollection() {
        // Nothing to do
    }


    @Override
    public boolean databaseCreationCreatesSchema() {
        return false;
    }


    @Override
    public boolean isUsingRustBackend() {
        return false;
    }
}
