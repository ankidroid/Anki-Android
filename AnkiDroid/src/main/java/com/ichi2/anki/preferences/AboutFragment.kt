/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.text.parseAsHtml
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.*
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.IntentUtil
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.copyToClipboard
import com.ichi2.utils.show

class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutView = inflater.inflate(R.layout.about_layout, container, false)

        // Version text
        layoutView.findViewById<TextView>(R.id.about_version).text = pkgVersionName

        // Logo secret
        layoutView.findViewById<ImageView>(R.id.about_app_logo)
            .setOnClickListener(DevOptionsSecretClickListener(requireActivity() as Preferences))

        // Contributors text
        val contributorsLink = getString(R.string.link_contributors)
        val contributingGuideLink = getString(R.string.link_contribution)
        layoutView.findViewById<TextView>(R.id.about_contributors_description).apply {
            text = getString(R.string.about_contributors_description, contributorsLink, contributingGuideLink).parseAsHtml()
            movementMethod = LinkMovementMethod.getInstance()
        }

        // License text
        val gplLicenseLink = getString(R.string.licence_wiki)
        val agplLicenseLink = getString(R.string.link_agpl_wiki)
        val sourceCodeLink = getString(R.string.link_source)
        layoutView.findViewById<TextView>(R.id.about_license_description).apply {
            text = getString(R.string.license_description, gplLicenseLink, agplLicenseLink, sourceCodeLink).parseAsHtml()
            movementMethod = LinkMovementMethod.getInstance()
        }

        // Donate text
        val donateLink = getString(R.string.link_opencollective_donate)
        layoutView.findViewById<TextView>(R.id.about_donate_description).apply {
            text = getString(R.string.donate_description, donateLink).parseAsHtml()
            movementMethod = LinkMovementMethod.getInstance()
        }

        // Rate Ankidroid button
        layoutView.findViewById<Button>(R.id.about_rate).setOnClickListener {
            IntentUtil.tryOpenIntent((requireActivity() as AnkiActivity), AnkiDroidApp.getMarketIntent(requireContext()))
        }

        // Open changelog button
        layoutView.findViewById<Button>(R.id.about_open_changelog).setOnClickListener {
            val openChangelogIntent = Intent(requireContext(), Info::class.java).apply {
                putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
            }
            startActivity(openChangelogIntent)
        }

        // Copy debug info button
        layoutView.findViewById<Button>(R.id.about_copy_debug).setOnClickListener {
            copyDebugInfo()
        }

        return layoutView
    }

    /**
     * Copies debug info (from [DebugInfoService.getDebugInfo]) to the clipboard
     */
    private fun copyDebugInfo() {
        val debugInfo = DebugInfoService.getDebugInfo(requireContext())
        if (requireContext().copyToClipboard(debugInfo)) {
            showSnackbar(
                R.string.about_ankidroid_successfully_copied_debug_info,
                Snackbar.LENGTH_SHORT
            )
        } else {
            showSnackbar(
                R.string.about_ankidroid_error_copy_debug_info,
                Snackbar.LENGTH_SHORT
            )
        }
    }

    /**
     * Click listener which enables developer options on release builds
     * if the user clicks it a minimum number of times
     */
    private class DevOptionsSecretClickListener(val preferencesActivity: Preferences) : View.OnClickListener {
        private var clickCount = 0
        private val clickLimit = 6

        override fun onClick(view: View) {
            if (DevOptionsFragment.isEnabled(view.context)) {
                return
            }
            if (++clickCount == clickLimit) {
                showEnableDevOptionsDialog(view.context)
            }
        }

        /**
         * Shows a dialog to confirm if developer options should be enabled or not
         */
        fun showEnableDevOptionsDialog(context: Context) {
            AlertDialog.Builder(context).show {
                setTitle(R.string.dev_options_enabled_pref)
                setIcon(R.drawable.ic_warning)
                setMessage(R.string.dev_options_warning)
                setPositiveButton(R.string.dialog_ok) { _, _ -> enableDevOptions() }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> clickCount = 0 }
                setCancelable(false)
            }
        }

        /**
         * Enables developer options for the user and shows it on [HeaderFragment]
         */
        fun enableDevOptions() {
            preferencesActivity.setDevOptionsEnabled(true)
            preferencesActivity.showSnackbar(R.string.dev_options_enabled_msg) {
                setAction(R.string.undo) { preferencesActivity.setDevOptionsEnabled(false) }
            }
        }
    }
}
