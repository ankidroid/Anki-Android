package com.ichi2.anki

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.preferences.SyncSettingsFragment.Companion.LOGIN_STATUS_CHANGED_REQUEST_KEY
import com.ichi2.anki.web.HostNumFactory

/**
 * Shows the AnkiWeb logged-in e-mail and a button to logout ([R.layout.my_account_logged_in])
 */
class LoggedInFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isLoggedIn()) {
            changeToLoginFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.my_account_logged_in, null)
        val username = AnkiDroidApp.getSharedPrefs(requireContext()).getString("username", "")
        view.findViewById<TextView>(R.id.username_logged_in).text = username
        view.findViewById<Button>(R.id.logout_button).setOnClickListener { logout() }
        return view
    }

    private fun changeToLoginFragment() {
        parentFragmentManager.popBackStack()
        parentFragmentManager.commit {
            replace(R.id.fragment_container, LoginFragment())
            addToBackStack(null)
        }
        setFragmentResult(LOGIN_STATUS_CHANGED_REQUEST_KEY, bundleOf(LOGIN_STATUS_CHANGED_REQUEST_KEY to true))
    }

    private fun logout() {
        launchCatchingTask {
            syncLogout(requireContext())
            HostNumFactory.getInstance(requireContext()).reset()
            changeToLoginFragment()
        }
    }
}
