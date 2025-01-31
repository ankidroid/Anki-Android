/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.account

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.pages.RemoveAccountFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.syncLogout
import com.ichi2.anki.utils.ext.removeFragmentFromContainer
import com.ichi2.anki.utils.ext.showDialogFragment
import timber.log.Timber

class LoggedInFragment : Fragment(R.layout.my_account_logged_in) {
    private val isScreenSmall: Boolean
        get() = (
            (
                requireActivity()
                    .applicationContext.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            ) <
                Configuration.SCREENLAYOUT_SIZE_LARGE
        )

    private lateinit var loggedInLogo: ImageView

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar?.title = getString(R.string.sync_account)

        view.findViewById<TextView>(R.id.username_logged_in).text = Prefs.username

        view.findViewById<Button>(R.id.privacy_policy_button).apply {
            setOnClickListener { openAnkiDroidPrivacyPolicy() }
        }

        view.findViewById<Button>(R.id.logout_button).apply {
            setOnClickListener { logout() }
        }
        view.findViewById<Button>(R.id.remove_account_button).apply {
            setOnClickListener { openRemoveAccountScreen() }
        }

        loggedInLogo = view.findViewById(R.id.login_logo)
    }

    private fun openAnkiDroidPrivacyPolicy() {
        Timber.i("Opening 'Privacy policy'")
        showDialogFragment(HelpDialog.newPrivacyPolicyInstance())
    }

    private fun logout() {
        launchCatchingTask {
            syncLogout(requireContext())
            val fragmentManager = requireActivity().supportFragmentManager
            fragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, LoginFragment())
                .commit()
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    /**
     * Opens the AnkiWeb 'remove account' WebView
     * @see RemoveAccountFragment
     * @see R.string.remove_account_url
     */
    private fun openRemoveAccountScreen() {
        Timber.i("opening 'remove account'")
        requireActivity()
            .supportFragmentManager
            .beginTransaction()
            .replace(R.id.remove_account_frame, RemoveAccountFragment())
            .commit()
        requireView().findViewById<View>(R.id.remove_account_frame).isVisible = true
        requireView().findViewById<View>(R.id.logged_in_layout).isVisible = false
        onRemoveAccountBackCallback.isEnabled = true
    }

    // if the 'remove account' fragment is open, close it first
    private val onRemoveAccountBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                closeRemoveAccountScreen()
            }
        }

    private fun closeRemoveAccountScreen() {
        Timber.i("closing 'remove account'")
        requireActivity().supportFragmentManager.removeFragmentFromContainer(R.id.remove_account_frame)
        requireView().findViewById<View>(R.id.remove_account_frame).isVisible = false
        requireView().findViewById<View>(R.id.logged_in_layout).isVisible = true
        onRemoveAccountBackCallback.isEnabled = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loggedInLogo.visibility =
            if (isScreenSmall && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
            ) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }
}
