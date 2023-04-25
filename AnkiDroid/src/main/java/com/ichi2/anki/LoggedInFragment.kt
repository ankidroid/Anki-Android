package com.ichi2.anki

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ichi2.anki.web.HostNumFactory

/**
 * Shows the AnkiWeb logged-in e-mail and a button to logout ([R.layout.my_account_logged_in])
 */
class LoggedInFragment : Fragment() {
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
        TODO() // will be changed on a later commit
    }

    private fun logout() {
        launchCatchingTask {
            syncLogout(requireContext())
            HostNumFactory.getInstance(requireContext()).reset()
            changeToLoginFragment()
        }
    }
}
