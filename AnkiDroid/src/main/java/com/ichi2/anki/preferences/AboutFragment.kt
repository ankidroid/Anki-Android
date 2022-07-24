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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.parseAsHtml
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.*
import com.ichi2.anki.Preferences.DevOptionsFragment
import com.ichi2.anki.dialogs.HelpDialog
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.utils.IntentUtil
import com.ichi2.utils.VersionUtils.appName
import com.ichi2.utils.VersionUtils.pkgVersionName

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
            .setOnClickListener(DevOptionsSecretClickListener(parentFragmentManager))

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

        // Send feedback button
        layoutView.findViewById<Button>(R.id.about_send_feedback).setOnClickListener {
            HelpDialog.openFeedback((requireActivity() as AnkiActivity))
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
        val debugInfo = DebugInfoService.getDebugInfo(requireContext()) { (requireActivity() as Preferences).col }
        val clipboardManager = requireActivity().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("$appName v$pkgVersionName", debugInfo))
            UIUtils.showThemedToast(context, getString(R.string.about_ankidroid_successfully_copied_debug), true)
        } else {
            UIUtils.showThemedToast(context, getString(R.string.about_ankidroid_error_copy_debug_info), false)
        }
    }

    /**
     * Click listener which enables developer options on release builds
     * if the user clicks it a minimum number of times
     */
    private class DevOptionsSecretClickListener(val fragmentManager: FragmentManager) : View.OnClickListener {
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
            MaterialDialog(context).show {
                title(R.string.dev_options_enabled_pref)
                icon(R.drawable.ic_warning_black)
                message(R.string.dev_options_warning)
                positiveButton(R.string.dialog_ok) { enableDevOptions(context) }
                // Reset click count if user has cancelled the action
                negativeButton(R.string.dialog_cancel) { clickCount = 0 }
                // avoid dismissing the dialog, as there is a high chance the user
                // taps outside the dialog while trying to unlock the secret
                cancelOnTouchOutside(false)
            }
        }

        /**
         * Enables developer options for the user and shows it on [Preferences.HeaderFragment]
         */
        fun enableDevOptions(context: Context) {
            DevOptionsFragment.setDevOptionsEnabledByUser(context, true)

            val message = context.getString(R.string.dev_options_enabled_msg)
            UIUtils.showThemedToast(context, message, true)

            // Make developer options visible on headers fragment if it is open
            val headersFragment = fragmentManager.findFragmentByTag(Preferences.HeaderFragment::class.java.name)
            if (headersFragment is Preferences.HeaderFragment) {
                headersFragment.setDevOptionsVisibility(true)
            }
        }
    }
}
