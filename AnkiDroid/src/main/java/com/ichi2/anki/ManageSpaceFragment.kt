/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>                          *
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
package com.ichi2.anki

import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.CollectionManager.deleteCollectionDirectory
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.themes.StyledProgressDialog
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ManageSpaceFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.manage_space
    override val analyticsScreenNameConstant: String
        get() = "manageSpace"

    override fun initSubscreen() {
        requirePreference<Preference>(R.string.delete_collection_key).setOnPreferenceClickListener {
            MaterialDialog(requireContext()).show {
                title(R.string.delete_collection)
                message(R.string.delete_data_confirmation)
                positiveButton(R.string.dialog_positive_delete) {
                    val progressDialog = StyledProgressDialog.show(requireContext(), getString(R.string.delete_data_ongoing), null)
                    launchCatchingTask {
                        // TODO: close main ankidroid activity
                        // TODO: Uses withProgress if we move to FragmentActivity
                        deleteCollectionDirectory()
                        MainScope().launch {
                            progressDialog.dismiss()
                        }
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
            true
        }
    }
}
