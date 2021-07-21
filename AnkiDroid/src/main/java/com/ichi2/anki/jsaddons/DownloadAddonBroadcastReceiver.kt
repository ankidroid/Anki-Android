/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01></infinyte01>@gmail.com>                                       *
 * *
 * *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 * *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                           *
 */

package com.ichi2.anki.jsaddons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.jsaddons.NpmPackageDownloader.DownloadAddon
import com.ichi2.anki.jsaddons.NpmPackageDownloader.DownloadAddonListener
import com.ichi2.anki.jsaddons.NpmUtils.getAddonNameFromUrl
import com.ichi2.async.TaskManager
import timber.log.Timber

class DownloadAddonBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.data.toString()
        val addonName = getAddonNameFromUrl(url)

        // if user clicked 'Install Addon' from package search url https://www.npmjs.com/search
        // instead of npm package url https://www.npmjs.com/package
        if (addonName == null) {
            UIUtils.showThemedToast(context, context.getString(R.string.invalid_js_addon), false)
            return
        }
        Timber.d("Addon Url::%s", url)
        TaskManager.launchCollectionTask(DownloadAddon(context, addonName), DownloadAddonListener(context))
    }
}
