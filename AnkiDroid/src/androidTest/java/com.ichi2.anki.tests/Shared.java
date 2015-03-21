/****************************************************************************************
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.tests;

import android.content.Context;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.hooks.Hooks;

import java.io.File;
import java.io.IOException;

/**
 * Shared methods for unit tests.
 */
public class Shared {

    public static Collection getEmptyCol(Context context) throws IOException {
        File f = File.createTempFile("test", ".anki2");
        // Provide a string instead of an actual File. Storage.Collection won't populate the DB
        // if the file already exists (it assumes it's an existing DB).
        String path = f.getAbsolutePath();
        f.delete();
        Hooks.getInstance(context);    // libanki doesn't have an internal context so initialize here
        return Storage.Collection(path);
    }

    
    /**
     * @return A File object pointing to a directory in which temporary test files can be placed. The directory is
     *         emptied on every invocation of this method so it is suitable to use at the start of each test.
     *         Only add files (and not subdirectories) to this directory.
     */
    public static File getTestDir(Context context) {
        File dir = new File(context.getCacheDir(), "testfiles");
        dir.mkdir();
        for (File f : dir.listFiles()) {
            f.delete();
        }
        return dir;
    }
}
