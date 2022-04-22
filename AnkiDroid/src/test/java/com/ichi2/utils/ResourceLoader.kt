/*
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;

import android.content.Context;
import android.text.TextUtils;

import com.ichi2.libanki.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.junit.Assert.assertTrue;

public class ResourceLoader {

    /**
     * Copy a file from the application's assets directory and return the absolute path of that
     * copy.
     *
     * Files located inside the application's assets collection are not stored on the file
     * system and can not return a usable path, so copying them to disk is a requirement.
     */
    public static String getTempFilePath(Context context, String name, String newName) {
        try {
            InputStream is = context.getClassLoader().getResourceAsStream(name);
            if (is == null) {
                throw new FileNotFoundException("Could not find test file: " + name);
            }
            File file = new File(getTestDir(context, name), newName);
            String dst = file.getAbsolutePath();
            Utils.writeToFile(is, dst);
            file.deleteOnExit();
            return dst;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getTempCollection(Context context, String name) {
        return getTempFilePath(context, name, "collection.anki2");
    }

    /**
     * @param name An additional suffix to ensure the test directory is only used by a particular resource.
     * @return A File object pointing to a directory in which temporary test files can be placed. The directory is
     *      emptied on every invocation of this method so it is suitable to use at the start of each test.
     *      Only add files (and not subdirectories) to this directory.
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
}
