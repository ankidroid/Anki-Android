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
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.async.deleteMedia
import com.ichi2.themes.StyledProgressDialog

class ManageSpaceFragment : SettingsFragment() {
    override val preferenceResource = R.xml.manage_space
    override val analyticsScreenNameConstant = "manageSpace"

    override fun initSubscreen() {
        if (!CollectionManager.collectionDirectoryExists()) {
            disableMenuEntries()
        }
        requirePreference<Preference>(R.string.delete_collection_key).setOnPreferenceClickListener {
            MaterialDialog(requireContext()).show {
                title(R.string.delete_collection)
                message(R.string.delete_collection_confirmation)
                positiveButton(R.string.dialog_positive_delete) {
                    launchCatchingTask {
                        withStyledProgressDialogShowing(R.string.delete_collection_ongoing) {}
                        // TODO: close main ankidroid activity
                        // TODO: Uses withProgress if we move to FragmentActivity
                        deleteCollectionDirectory()
                        disableMenuEntries()
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
            true
        }
        requirePreference<Preference>(R.string.check_media_key).setOnPreferenceClickListener {
            MaterialDialog(requireContext()).show {
                title(R.string.check_media)
                message(R.string.check_media_warning)
                positiveButton(R.string.dialog_ok) {
                    val progressDialog = StyledProgressDialog.show(requireContext(), getString(R.string.delete_collection_ongoing), null)
                    launchCatchingTask {
                        // TODO: Uses withProgress if we move to FragmentActivity
                        val mediaCheckResult = withCol { media.fullCheck() }
                        val unused = mediaCheckResult.unused
                        if (unused.isEmpty()) {
                            // TODO: Let the user know there is nothing to do
                            return@launchCatchingTask
                        }
                        progressDialog.dismiss()
                        askUserToConfirmUnusedMediaDeletionAndPerformDeletion(unused)
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
            true
        }
    }

    suspend fun <T> MaterialDialog.withStyledProgressDialogShowing(title: Int, block: suspend () -> T): T {
        val progressDialog = StyledProgressDialog.show(
            context,
            resources.getString(title),
            null
        )
        val v = block()
        progressDialog.dismiss()
        return v
    }

    /**
     * Asks the user whether they want to delete the N unused medias.
     */
    // TODO: Show the content of [unused] to the user and ask them to confirm they are okay with
    // deleting those files. Instead of just asking "are you okay to deleting unused files"
    private fun askUserToConfirmUnusedMediaDeletionAndPerformDeletion(
        unusedFiles: List<String>
    ) {
        val numberOfUnusedMedias = unusedFiles.size
        MaterialDialog(requireContext()).show {
            title(R.string.check_media)
            message(text = context.resources.getQuantityString(R.string.check_media_summary, numberOfUnusedMedias))
            positiveButton(R.string.dialog_ok) {
                launchCatchingTask {
                    withStyledProgressDialogShowing(R.string.delete_media_message) {
                        // TODO: Uses withProgress if we move to FragmentActivity
                        withCol { deleteMedia(this, unusedFiles) }
                    }
                }
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun disableMenuEntries() {
        for (key in listOf(R.string.delete_collection_key, R.string.check_media_key, R.string.pref_backup_max_key)) {
            requirePreference<Preference>(key).apply {
                isEnabled = false
                isSelectable = false
            }
        }
    }
}
