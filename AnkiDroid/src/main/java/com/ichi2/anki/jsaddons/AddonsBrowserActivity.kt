/****************************************************************************************
 * Copyright (c) 2022 Mani <infinyte01@gmail.com>                                       *
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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.showThemedToast
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Shows the addons available in the app along with actions to fetch and remove other addons.
 */
// TODO ===== EXTRACT RELATED STRINGS FOR TRANSLATION BEFORE RELEASE =====
class AddonsBrowserActivity : AnkiActivity() {
    private val addonsAdapter by lazy {
        AddonsBrowserAdapter(
            context = this@AddonsBrowserActivity,
            onToggleAddon = ::handleAddonModelToggled,
            onDetailsRequested = { addonModel ->
                showDialogFragment(this, AddonDetailsDialog.newInstance(addonModel))
            },
            onDeleteAddon = ::handleAddonDeletion
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addons_browser)

        enableToolbar().apply {
            // Add a home button to the actionbar
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            title = TR.addonsWindowTitle()
        }

        findViewById<RecyclerView>(R.id.addons_list).apply {
            adapter = addonsAdapter
        }
        findViewById<Button>(R.id.addon_get).apply {
            text = TR.addonsGetAddons()
            setOnClickListener {
                showThemedToast(
                    this@AddonsBrowserActivity,
                    "\"Get addons\" not implemented yet!",
                    true
                )
            }
        }
        findViewById<Button>(R.id.addon_install_from_file).apply {
            text = TR.addonsInstallFromFile()
            setOnClickListener {
                showThemedToast(
                    this@AddonsBrowserActivity,
                    "\"Install from file\" not implemented yet!",
                    true
                )
            }
        }
        loadAddonsForCurrentProfile()
    }

    /**
     * List addons from directory, for valid package.json the addons will be added to view
     */
    private fun loadAddonsForCurrentProfile() {
        Timber.d("Listing addons from directory")
        // AnkiDroid/addons/
        val addonsDir = AddonStorage(this).getCurrentProfileAddonDir()
        val foundAddons = mutableListOf<AddonModelUi>()

        val availableFiles: Array<File>? = addonsDir.listFiles()
        if (availableFiles == null) {
            showThemedToast(this, "Couldn't load addons from directory!", false)
            return
        }
        availableFiles.forEach { file ->
            try {
                Timber.d("Parsing addon: %s", file.name)
                // AnkiDroid/addons/some-addon/package/package.json
                val packageJsonPath = AddonStorage(this).getSelectedAddonPackageJson(file.name).path
                val result: Pair<AddonModel?, List<String>> = getAddonModelFromJson(packageJsonPath)
                val addonModel = result.first
                if (addonModel != null) {
                    val enabledAddonSet =
                        sharedPrefs().getStringSet(addonModel.addonType, hashSetOf()) ?: emptySet()
                    foundAddons.add(
                        AddonModelUi(
                            modelRef = addonModel,
                            isEnabled = enabledAddonSet.contains(addonModel.name)
                        )
                    )
                }
            } catch (e: IOException) {
                Timber.w(e)
                showThemedToast(
                    this,
                    "${file.name} is not recognized as a valid addon",
                    false
                )
            }
        }
        // Adding one addon just for testing, remove this if releasing
        val testAddonModel = AddonModel(
            name = "Test addon",
            addonTitle = "Test addon",
            description = "This is a test addon entry",
            icon = "R",
            version = "1.0.0",
            ankidroidJsApi = "1.0.0",
            main = "index.js",
            addonType = "Reviewer",
            keywords = listOf("addon, test"),
            author = mapOf("name" to "AnkiDroid", "url" to "https://www.ankidroid.org"),
            license = "MIT",
            homepage = "https://ankiweb.net",
            dist = DistInfo("")
        )
        foundAddons += AddonModelUi(testAddonModel, false)
        addonsAdapter.submitList(foundAddons)
        findViewById<LinearLayout>(R.id.no_addons_found_msg).visibility =
            if (foundAddons.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.addons, menu)
        menu.findItem(R.id.action_check_for_updates)?.apply {
            icon = ContextCompat.getDrawable(this@AddonsBrowserActivity, R.drawable.ic_update)
            title = TR.addonsCheckForUpdates()
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> return super.onOptionsItemSelected(item)
            R.id.action_check_for_updates -> showThemedToast(
                this,
                "\"Check for updates\" not implemented yet!",
                true
            )
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleAddonModelToggled(addonModel: AddonModel, isChecked: Boolean) {
        addonModel.updatePrefs(sharedPrefs(), addonModel.addonType, !isChecked)
        showThemedToast(
            this,
            if (isChecked) "Addon ${addonModel.addonTitle} was enabled" else "Addon ${addonModel.addonTitle} was disabled",
            true
        )
    }

    private fun handleAddonDeletion(addonModel: AddonModel) {
        val dialog = ConfirmationDialog().apply {
            setArgs(addonModel.addonTitle, "Remove addon ${addonModel.addonTitle}?")
            val confirm = Runnable {
                Timber.i("AddonsAdapter:: Deleting addon %s", addonModel.addonTitle)
                val deleteAddon =
                    AddonStorage(this@AddonsBrowserActivity)
                        .deleteSelectedAddonPackageDir(addonModel.name)
                if (!deleteAddon) {
                    showThemedToast(
                        this@AddonsBrowserActivity,
                        "Failed to remove addon. Please delete manually",
                        false
                    )
                    return@Runnable
                }
                // update prefs for removed addon
                addonModel.updatePrefs(sharedPrefs(), addonModel.addonType, true)
                loadAddonsForCurrentProfile()
            }
            setConfirm(confirm)
        }
        showDialogFragment(this, dialog)
    }

    override fun onResume() {
        super.onResume()
        loadAddonsForCurrentProfile()
    }
}
