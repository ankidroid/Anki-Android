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
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Utils
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Implemented functions for getting addons related directory for current profile
 */
class AddonStorage(val context: Context) {
    private val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context)
    private var addonsHomeDir: File = File(currentAnkiDroidDirectory, "addons")

    /**
     * Untar and ungzip a tar.gz file to a AnkiDroid/addons directory.
     *
     * @param tarballFile the .tgz file to extract
     * @param addonName the current selected addon name, so it is extracted in addons directory
     *                         e.g. AnkiDroid/addons/addon-name/
     * @return the temp directory.
     * @throws FileNotFoundException if .tgz file or ungzipped file i.e. .tar file not found
     * @throws IOException
     */
    @Throws(Exception::class)
    fun extractTarGzipToAddonDir(tarballFile: File, addonName: String) {
        val packageExtract = TgzPackageExtract(context)
        val addonsPackageDir = getSelectedAddonDir(addonName)

        require(packageExtract.isGzip(tarballFile)) { context.getString(R.string.not_valid_js_addon, tarballFile.absolutePath) }

        try {
            CompatHelper.compat.createDirectories(addonsPackageDir)
        } catch (e: IOException) {
            UIUtils.showThemedToast(context, context.getString(R.string.could_not_create_dir, addonsPackageDir.absolutePath), false)
            Timber.w(e)
            return
        }

        // Make sure we have 2x the tar file size in free space (1x for tar file, 1x for unarchived tar file contents
        val requiredMinSpace = tarballFile.length() * 2
        val availableSpace = Utils.determineBytesAvailable(addonsPackageDir.canonicalPath)
        TgzPackageExtract.InsufficientSpaceException.throwIfInsufficientSpace(context, requiredMinSpace, availableSpace)

        // If space available then unGZip it
        val tarTempFile = packageExtract.unGzip(tarballFile, addonsPackageDir)
        tarTempFile.deleteOnExit()

        // Make sure we have sufficient free space
        val unTarSize = packageExtract.calculateUnTarSize(tarTempFile)
        TgzPackageExtract.InsufficientSpaceException.throwIfInsufficientSpace(context, unTarSize, availableSpace)

        try {
            // If space available then unTar it
            packageExtract.unTar(tarTempFile, addonsPackageDir)
        } catch (e: IOException) {
            Timber.w("Failed to unTar file")
            deleteSelectedAddonPackageDir(addonsPackageDir.path)
        } finally {
            tarTempFile.delete()
        }
    }

    /**
     * Get addons directory for current profile
     * e.g. AnkiDroid/addons/
     */
    fun getCurrentProfileAddonDir(): File? {
        try {
            CompatHelper.compat.createDirectories(addonsHomeDir)
        } catch (e: IOException) {
            UIUtils.showThemedToast(context, context.getString(R.string.could_not_create_dir, addonsHomeDir.absolutePath), false)
            Timber.w(e)
            return null
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
        addonsHomeDir = getCurrentProfileAddonDir()!!
        return File(addonsHomeDir, addonName)
    }

    /**
     * Get package dir for selected addon
     * e.g. AnkiDroid/addons/some-addon/package/
     *
     * @param addonName
     * @return package dir file
     */
    fun getSelectedAddonPackageDir(addonName: String): File {
        val addonPath = getSelectedAddonDir(addonName)
        return File(addonPath, "package")
    }

    /**
     * Get package.json for selected addon
     * e.g. AnkiDroid/addons/some-addon/package/package.json
     *
     * @param addonName
     * @return package.json file
     */
    fun getSelectedAddonPackageJson(addonName: String): File {
        val addonPackageDir = getSelectedAddonPackageDir(addonName)
        return File(addonPackageDir, "package.json")
    }

    /**
     * Get index.js for selected addon
     * e.g. AnkiDroid/addons/some-addon/package/index.js
     *
     * @param addonName
     * @return index.js file for adding it reviewer or note editor
     */
    fun getSelectedAddonIndexJs(addonName: String): File {
        val addonPackageDir = getSelectedAddonPackageDir(addonName)
        return File(addonPackageDir, "index.js")
    }

    /**
     * Get README.md for selected addon, it will be used to show selected addon related content
     * e.g. AnkiDroid/addons/some-addon/package/README.md
     *
     * @param addonName
     * @return README.md file
     */
    fun getSelectedAddonReadme(addonName: String): File {
        val addonPackageDir = getSelectedAddonPackageDir(addonName)
        return File(addonPackageDir, "README.md")
    }

    /**
     * Remove selected addon from addons directory
     *
     * @param addonName
     */
    fun deleteSelectedAddonPackageDir(addonName: String): Boolean {
        if (File(addonName).parent != "addons") {
            return false
        }

        val dir = getSelectedAddonDir(addonName)
        return BackupManager.removeDir(dir)
    }
}
