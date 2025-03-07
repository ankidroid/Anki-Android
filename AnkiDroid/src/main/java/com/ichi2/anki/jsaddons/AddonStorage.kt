/****************************************************************************************
 * Copyright (c) 2022 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
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
 ***************************************************************************************/

package com.ichi2.anki.jsaddons

import android.content.Context
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionHelper
import com.ichi2.annotations.NeedsTest
import java.io.File

/**
 * Implemented functions for getting addons related directory for current profile
 */
@NeedsTest("Addons directory test")
class AddonStorage(val context: Context) {
    private val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
    private val addonsHomeDir = File(currentAnkiDroidDirectory, "addons")

    /**
     * Get addons directory for current profile
     * e.g. AnkiDroid/addons/
     */
    fun getCurrentProfileAddonDir(): File {
        if (!addonsHomeDir.exists()) {
            addonsHomeDir.mkdirs()
        }
        return addonsHomeDir
    }

    /**
     * Get addon's directory which contains packages and index.js files
     * e.g. AnkiDroid/addons/some-addon/
     *
     * @param addonName
     * @return some-addon dir e.g. AnkiDroid/addons/some-addon/
     */
    fun getSelectedAddonDir(addonName: String): File {
        return File(addonsHomeDir, addonName)
    }

    /**
     * Get package.json for selected addons
     * e.g. AnkiDroid/addons/some-addon/package/package.json
     *
     * @param addonName
     * @return package.json file
     */
    fun getSelectedAddonPackageJson(addonName: String): File {
        val addonPath = getSelectedAddonDir(addonName)
        return File(addonPath, "package/package.json")
    }

    /**
     * Remove selected addon in list view from addons directory
     *
     * @param addonName
     */
    fun deleteSelectedAddonPackageDir(addonName: String): Boolean {
        val dir = getSelectedAddonDir(addonName)
        return BackupManager.removeDir(dir)
    }
}
