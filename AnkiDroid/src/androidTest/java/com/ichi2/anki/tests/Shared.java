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
import android.text.TextUtils;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertTrue;

/**
 * Shared methods for unit tests.
 */
public class Shared {

    public static Collection getEmptyCol(Context context) throws IOException {
        File f = File.createTempFile("test", ".anki2");
        // Provide a string instead of an actual File. Storage.Collection won't populate the DB
        // if the file already exists (it assumes it's an existing DB).
        String path = f.getAbsolutePath();
        assertTrue(f.delete());
        return Storage.Collection(context, path);
    }


    /**
     * @return A File object pointing to a directory in which temporary test files can be placed. The directory is
     *         emptied on every invocation of this method so it is suitable to use at the start of each test.
     *         Only add files (and not subdirectories) to this directory.
     */
    public static File getTestDir(Context context) {
        return getTestDir(context, "");
    }



    /**
     * @param name An additional suffix to ensure the test directory is only used by a particular resource.
     * @return See getTestDir.
     */
    private static File getTestDir(Context context, String name) {
        String suffix = "";
        if (!TextUtils.isEmpty(name)) {
            suffix = "-" + name;
        }
        File dir = new File(context.getCacheDir(), "testfiles" + suffix);
        if (!dir.exists()) {
            assertTrue(dir.mkdir());
        }
        File[] files = dir.listFiles();
        if (files == null) {
            // Had this problem on an API 16 emulator after a stress test - directory existed
            // but listFiles() returned null due to EMFILE (Too many open files)
            // Don't throw here - later file accesses will provide a better exception.
            // and the directory exists, even if it's unusable.
            return dir;
        }

        for (File f : files) {
            assertTrue(f.delete());
        }
        return dir;
    }


    /**
     * Copy a file from the application's assets directory and return the absolute path of that
     * copy.
     *
     * Files located inside the application's assets collection are not stored on the file
     * system and can not return a usable path, so copying them to disk is a requirement.
     */
    public static String getTestFilePath(Context context, String name) throws IOException {
        InputStream is = context.getClassLoader().getResourceAsStream("assets/" + name);
        if (is == null) {
            throw new FileNotFoundException("Could not find test file: assets/" + name);
        }
        String dst = new File(getTestDir(context, name), name).getAbsolutePath();
        Utils.writeToFile(is, dst);
        return dst;
    }
}
