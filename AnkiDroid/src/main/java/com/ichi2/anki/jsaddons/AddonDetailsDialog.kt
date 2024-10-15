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
 ****************************************************************************************/

package com.ichi2.anki.jsaddons

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.R
import com.ichi2.utils.cancelable
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.positiveButton

/**
 * Shows all available details for the addon identified by the [AddonModel] passed as an argument.
 */
class AddonDetailsDialog : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val addonModel =
            BundleCompat.getParcelable(requireArguments(), KEY_ADDON_MODEL, AddonModel::class.java)
                ?: error("No addon identifier was provided!")
        val contentView =
            requireActivity().layoutInflater.inflate(R.layout.dialog_addon_details, null).apply {
                findViewById<TextView>(R.id.addon_name).text = addonModel.name
                findViewById<TextView>(R.id.addon_description).text = addonModel.description
                findViewById<TextView>(R.id.addon_type).text = addonModel.addonType
                findViewById<TextView>(R.id.addon_author).text = addonModel.author["name"]
                findViewById<TextView>(R.id.addon_version).text = addonModel.version
                findViewById<TextView>(R.id.addon_js_api_version).text = addonModel.ankidroidJsApi
                findViewById<TextView>(R.id.addon_license).text = addonModel.license
                findViewById<TextView>(R.id.addon_homepage).text = addonModel.homepage
            }
        return AlertDialog.Builder(requireContext()).create {
            customView(contentView)
            cancelable(true)
            positiveButton(R.string.close)
        }
    }

    companion object {
        private const val KEY_ADDON_MODEL = "key_addon_model"

        fun newInstance(addonModel: AddonModel): AddonDetailsDialog = AddonDetailsDialog().apply {
            arguments = bundleOf(KEY_ADDON_MODEL to addonModel)
        }
    }
}
