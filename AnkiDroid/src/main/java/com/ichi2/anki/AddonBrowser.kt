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
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.jsaddons.*
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import com.ichi2.async.TaskManager
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * A menu 'Addons' added to side Navigation drawer
 * When 'Addons' is clicked, it will open Addon Browser screen for listing all addons from directory.
 * Also 'Get Addons' menu added to option menu of this Activity, view onCreateOptionsMenu below
 */
class AddonBrowser : NavigationDrawerActivity(), SubtitleListener {
    private lateinit var mAddonsList: MutableList<AddonModel>
    private lateinit var mAddonsListRecyclerView: RecyclerView
    private val GET_ADDONS = "get_addons"
    private var mBooleanExtra: Boolean = false

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

        mAddonsListRecyclerView = findViewById(R.id.addons)
        mAddonsListRecyclerView.layoutManager = LinearLayoutManager(this)

        hideProgressBar()

        // if activity is created from Addons Browser or Get Addons
        mBooleanExtra = intent.getBooleanExtra(GET_ADDONS, false)
        if (mBooleanExtra) {
            fetchAddonsPackageJson()
        } else {
            listAddonsFromDir()
        }
    }

    /**
     * It adds a **Get More Addons** menu button to Addon Browser screen
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
            // clear items from recycler view with empty list
            mAddonsList.clear()
            mAddonsListRecyclerView.adapter?.notifyDataSetChanged()

            val intent = Intent(this, AddonBrowser::class.java)
            intent.putExtra(GET_ADDONS, true)
            startActivityWithAnimation(intent, ActivityTransitionAnimation.Direction.START)
            true
        }
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * List addons from directory, for valid package.json the addons will be added to view
     */
    private fun listAddonsFromDir() {
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this)

        // AnkiDroid/addons/
        val addonsDir = File(currentAnkiDroidDirectory, "addons")
        if (!addonsDir.exists()) {
            addonsDir.mkdirs()
        }

        Timber.d("List addon from directory.")

        mAddonsList = ArrayList()
        try {
            val files = addonsDir.listFiles()
            for (file in files!!) {
                Timber.d("Addons: %s", file.name)
                val mapper = ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

                val addonModel = mapper.readValue(File(file, "package/package.json"), AddonModel::class.java)

                if (addonModel.isValidAnkiDroidAddon()) {
                    mAddonsList.add(addonModel)
                }
            }

            if (mAddonsList.size == 0) {
                findViewById<LinearLayout>(R.id.no_addons_found_msg).visibility = View.VISIBLE
            } else {
                findViewById<LinearLayout>(R.id.no_addons_found_msg).visibility = View.GONE
            }

            mAddonsListRecyclerView.adapter = AddonsAdapter(mAddonsList)
        } catch (e: IOException) {
            Timber.w(e.localizedMessage)
            UIUtils.showThemedToast(this, getString(R.string.is_not_valid_js_addon), false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mBooleanExtra) {
            fetchAddonsPackageJson()
        } else {
            listAddonsFromDir()
        }
    }

    /**
     * fetch anki-js-addon, containing info about each addons, then add to view with download tarball url
     */
    private fun fetchAddonsPackageJson() {
        supportActionBar?.title = getString(R.string.download_addons)
        showProgressBar()

        // load items from anki-js-addon.json
        try {
            TaskManager.launchCollectionTask(
                NpmPackageDownloader.GetAddonsPackageJson(this),
                NpmPackageDownloader.GetAddonsPackageJsonListener(this, mAddonsListRecyclerView)
            )
        } catch (e: IOException) {
            Timber.w(e.localizedMessage)
            UIUtils.showThemedToast(this, getString(R.string.is_not_valid_js_addon), false)
        }
    }

    override val subtitleText: String?
        get() = resources.getString(R.string.js_addons)
}
