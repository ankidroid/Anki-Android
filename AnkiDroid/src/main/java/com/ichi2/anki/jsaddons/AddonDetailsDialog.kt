/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
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

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R

class AddonDetailsDialog(private val context1: Context, private var addonModel: AddonModel) : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        isCancelable = true
        dialog?.setTitle(addonModel.addonTitle)

        val view = inflater.inflate(R.layout.addon_details, null, false)
        val toolbar: Toolbar = view.findViewById(R.id.addon_toolbar)
        toolbar.setNavigationOnClickListener { dismiss() }

        view.findViewById<TextView>(R.id.detail_addon_title).text = addonModel.addonTitle
        view.findViewById<TextView>(R.id.addon_name).text = addonModel.name
        view.findViewById<TextView>(R.id.addon_description).text = addonModel.description
        view.findViewById<TextView>(R.id.addon_type).text = addonModel.addonType
        view.findViewById<TextView>(R.id.addon_author).text = addonModel.author?.get("name")
        view.findViewById<TextView>(R.id.addon_version).text = addonModel.version
        view.findViewById<TextView>(R.id.addon_js_api_version).text = addonModel.ankidroidJsApi
        view.findViewById<TextView>(R.id.addon_license).text = addonModel.license

        val homepageBtn = view.findViewById<TextView>(R.id.view_addon_homepage_button)
        val ankiActivity = context1 as AnkiActivity

        homepageBtn.setOnClickListener {
            dismiss()
            val uri = Uri.parse(addonModel.homepage)
            ankiActivity.openUrl(uri)
        }

        return view
    }
}
