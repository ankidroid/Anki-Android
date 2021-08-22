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

package com.ichi2.anki.importer

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import timber.log.Timber

/** Root fragment for importing a CSV
 * Maintains state external to the internal fragments (selected file/selected options)
 * Manages the state and transitions between activities
 */
@RequiresApi(api = Build.VERSION_CODES.O) // TextImporter -> FileObj
class ImporterHostFragment : Fragment(R.layout.generic_fragment_host) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fragmentManager = this.childFragmentManager

        fragmentManager.setFragmentResultListener(ImporterFileSelectionFragment.RESULT_KEY, this) { _, bundle ->

            Timber.i("handling %s", ImporterFileSelectionFragment.RESULT_KEY)

            val result = bundle.getString(ImporterFileSelectionFragment.RESULT_BUNDLE_FILE_PATH)

            fragmentManager.commit {
                replaceWith<ImporterOptionSelectionFragment>(bundleOf("filePath" to result))
            }
        }

        fragmentManager.setFragmentResultListener(ImporterOptionSelectionFragment.RESULT_KEY, this) { _, bundle ->
            Timber.i("handling %s", ImporterOptionSelectionFragment.RESULT_KEY)

            val result = bundle.getParcelable(ImporterOptionSelectionFragment.RESULT_BUNDLE_OPTIONS) as ImportOptions?

            if (result == null) {
                Timber.w("no result passed")
                UIUtils.showThemedToast(requireContext(), R.string.something_wrong, true)
                AnkiDroidApp.sendExceptionReport("failed to build bundle", "ImporterHostFragment")
                return@setFragmentResultListener
            }

            fragmentManager.commit {
                replaceWith<ImporterImportProgressFragment>(bundleOf("options" to result))
            }
        }

        if (savedInstanceState == null) {
            fragmentManager.commit {
                setReorderingAllowed(true)
                add<ImporterFileSelectionFragment>(R.id.fragment_container_view, "IMPORT")
            }
        }
    }

    /** Helper function to replace the root content view */
    private inline fun <reified T : Fragment> FragmentTransaction.replaceWith(bundle: Bundle) {
        replace(R.id.fragment_container_view, T::class.java, bundle)
    }
}
