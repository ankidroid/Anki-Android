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

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.NavigationDrawerActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import timber.log.Timber
import java.io.IOException

/**
 * A menu 'Addons' added to side Navigation drawer
 * When 'Addons' is clicked, it will open Addon Browser screen for listing all addons from directory.
 */
class AddonsBrowser : NavigationDrawerActivity(), SubtitleListener {
    private lateinit var addonsList: MutableList<AddonModel>
    private lateinit var addonsListRecyclerView: RecyclerView

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
        supportActionBar!!.title = getString(R.string.javascript_addons)
        showBackIcon()

        addonsListRecyclerView = findViewById(R.id.addons)
        addonsListRecyclerView.layoutManager = LinearLayoutManager(this)

        listAddonsFromCurrentProfile()
    }

    /**
     * List addons from directory, for valid package.json the addons will be added to view
     */
    private fun listAddonsFromCurrentProfile() {
        Timber.d("List addon from directory.")
        // AnkiDroid/addons/
        val addonsDir = AddonStorage(this).getCurrentProfileAddonDir()
        var tempAddonName = ""
        addonsList = ArrayList()
        try {
            val files = addonsDir.listFiles()
            for (file in files!!) {
                Timber.d("Addons: %s", file.name)
                tempAddonName = file.name
                // AnkiDroid/addons/some-addon/package/package.json
                val packageJsonPath = AddonStorage(this).getSelectedAddonPackageJson(file.name).path
                val result: Pair<AddonModel?, List<String>> = getAddonModelFromJson(packageJsonPath)
                val addonModel = result.first
                if (addonModel != null) {
                    addonsList.add(addonModel)
                }
            }

            findViewById<LinearLayout>(R.id.no_addons_found_msg).visibleIf(addonsList.size == 0)

            addonsListRecyclerView.adapter = AddonsBrowserAdapter(addonsList)
        } catch (e: IOException) {
            Timber.w(e)
            UIUtils.showThemedToast(this, getString(R.string.not_valid_js_addon, tempAddonName), false)
        }

        hideProgressBar()
    }

    override fun onResume() {
        super.onResume()
        listAddonsFromCurrentProfile()
    }

    override val subtitleText: String
        get() = resources.getString(R.string.javascript_addons)
}

fun View.visibleIf(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}
