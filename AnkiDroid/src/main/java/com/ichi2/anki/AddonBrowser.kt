/****************************************************************************************
 * Copyright (c) 2021 Mani infinyte01@gmail.com                                         *
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
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                  *
 *                                                                                      *
 * *************************************************************************************/

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import com.ichi2.anki.jsaddons.AddonDownloadActivity
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import timber.log.Timber
import java.io.File

/**
 * A menu 'Addons' added to side Navigation drawer
 * When 'Addons' is clicked, it will open Addon Browser screen for listing all addons from directory.
 * Also 'Get Addons' menu added to option menu of this Activity, view onCreateOptionsMenu below
 */
class AddonBrowser : NavigationDrawerActivity(), SubtitleListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        Timber.d("onCreate()")
        setContentView(R.layout.addons_browser)
        initNavigationDrawer(findViewById(android.R.id.content))

        // Add a home button to the actionbar
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.title = getString(R.string.js_addons)
        showBackIcon()
        hideProgressBar()
        listAddonsFromDir()
    }

    override fun getSubtitleText(): String {
        return resources.getString(R.string.js_addons)
    }

    /**
     * It adds a **Get More Addons** menu button to Addnon Browser screen
     * When **Get More Addons** button is clicked,
     * then it opens url https://www.npmjs.com/search?q=keywords:ankidroid-js-addon
     *
     * @param menu
     * @return true
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.addon_browser, menu)
        val getMoreAddons = menu.findItem(R.id.action_get_more_addons)
        getMoreAddons.setOnMenuItemClickListener {
            val intent = Intent(this, AddonDownloadActivity::class.java)
            startActivityWithoutAnimation(intent)
            true
        }
        return super.onCreateOptionsMenu(menu)
    }

    fun listAddonsFromDir() {
        // TODO
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this)

        // AnkiDroid/addons/
        val addonsDir = File(currentAnkiDroidDirectory, "addons")
        if (!addonsDir.exists()) {
            addonsDir.mkdirs()
        }
        Timber.d("List addon from directory.")
    }
}
