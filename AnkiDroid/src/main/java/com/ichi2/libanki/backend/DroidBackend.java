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

/**
 * Interface to the rust backend listing all currently supported functionality.
 */
public interface DroidBackend {
    DB openCollectionDatabase(String path);
    void closeCollection();

    /** Whether a call to {@link DroidBackend#openCollectionDatabase(String)} will generate a schema and indices for the database */
    boolean databaseCreationCreatesSchema();
}
