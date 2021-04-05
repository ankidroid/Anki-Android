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

import android.content.Context;

import com.ichi2.anki.BackupManager;
import com.ichi2.anki.CollectionHelper;

import org.robolectric.shadows.ShadowStatFs;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class BackupManagerTestUtilities {
    public static void setupSpaceForBackup(Context context) {
        String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context);

        String path = new File(currentAnkiDroidDirectory).getParentFile().getPath();
        ShadowStatFs.registerStats(path, 100, 20, 10000);

        assertTrue(BackupManager.enoughDiscSpace(currentAnkiDroidDirectory));
    }


    public static void reset() {
        ShadowStatFs.reset();
    }
}
