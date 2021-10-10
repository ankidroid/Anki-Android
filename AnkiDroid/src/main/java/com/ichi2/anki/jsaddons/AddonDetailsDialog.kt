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
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R

class AddonDetailsDialog(private val context1: Context, private var addonModel: AddonModel) : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        isCancelable = true
        dialog?.setTitle(addonModel.addonTitle)

        val view = inflater.inflate(R.layout.addon_details, null, false)
        val toolbar: Toolbar = view.findViewById(R.id.addon_toolbar)
        toolbar.setNavigationOnClickListener { dismiss() }

        view.findViewById<TextView>(R.id.detail_addon_title).text = addonModel.addonTitle

        val linearLayout = view.findViewById<View>(R.id.addon_details_linear_layout) as LinearLayout

        linearLayout.addView(createTitleTextView(context1.getString(R.string.npm_package_name)))
        linearLayout.addView(createContentTextView(addonModel.name))
        linearLayout.addView(createSeparator())

        linearLayout.addView(createTitleTextView(context1.getString(R.string.addon_description)))
        linearLayout.addView(createContentTextView(addonModel.description))
        linearLayout.addView(createSeparator())

        linearLayout.addView(createTitleTextView(context1.getString(R.string.addon_version)))
        linearLayout.addView(createContentTextView(addonModel.version))
        linearLayout.addView(createSeparator())

        linearLayout.addView(createTitleTextView(context1.getString(R.string.ankidroid_js_api_version)))
        linearLayout.addView(createContentTextView(addonModel.ankidroidJsApi))
        linearLayout.addView(createSeparator())

        linearLayout.addView(createTitleTextView(context1.getString(R.string.addon_type)))
        linearLayout.addView(createContentTextView(addonModel.addonType))
        linearLayout.addView(createSeparator())

        linearLayout.addView(createTitleTextView(context1.getString(R.string.addon_author)))
        linearLayout.addView(createContentTextView(addonModel.author?.get("name")))
        linearLayout.addView(createSeparator())

        linearLayout.addView(createTitleTextView(context1.getString(R.string.addon_license)))
        linearLayout.addView(createContentTextView(addonModel.license))
        linearLayout.addView(createSeparator())

        val homepageBtn = view.findViewById<Button>(R.id.view_addon_homepage_button)
        val updateBtn = view.findViewById<Button>(R.id.addon_update_button)
        val ankiActivity = context1 as AnkiActivity

        homepageBtn.setOnClickListener {
            dismiss()
            val uri = Uri.parse(addonModel.homepage)
            ankiActivity.openUrl(uri)
        }

        // when update button clicked then open package url from there user can install/update the addon
        updateBtn.setOnClickListener {
            dismiss()
            val intent = Intent(activity, AddonDownloadActivity::class.java)
            intent.putExtra("addon_name", addonModel.name)
            startActivity(intent)
        }

        return view
    }

    private fun createTitleTextView(text: String): TextView {
        val textView = TextView(context1)
        textView.text = text
        textView.textSize = 16f
        textView.setTypeface(null, Typeface.BOLD)
        textView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return textView
    }

    private fun createContentTextView(text: String?): TextView {
        val textView = TextView(context1)
        textView.text = text
        textView.textSize = 18f
        textView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return textView
    }

    private fun createSeparator(): View {
        val viewDivider = View(context1)
        val dividerHeightDP = resources.displayMetrics.density.toInt() * 2
        val marginDP = resources.displayMetrics.density.toInt() * 10
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dividerHeightDP)
        params.setMargins(0, marginDP, 0, marginDP)
        viewDivider.layoutParams = params
        viewDivider.setBackgroundColor(ContextCompat.getColor(context1, R.color.material_blue_grey_050))
        return viewDivider
    }
}
