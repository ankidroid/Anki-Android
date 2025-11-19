package com.ichi2.anki.account
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar

class AccountRemovalExplanationDialog : DialogFragment() {
    companion object {
        const val REQUEST_KEY = "account_removal_explanation"
        const val RESULT_PROCEED = "proceed"

        fun newInstance(): AccountRemovalExplanationDialog = AccountRemovalExplanationDialog()
    }

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val email = preferences.getString("username", "") ?: ""

        val view =
            LayoutInflater
                .from(requireContext())
                .inflate(R.layout.dialog_account_removal_explanation, null)

        val emailTextView: TextView = view.findViewById(R.id.email_text)
        val copyEmailButton: Button = view.findViewById(R.id.copy_email_button)

        emailTextView.text = email

        copyEmailButton.setOnClickListener {
            copyEmailToClipboard(email)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton(R.string.proceed) { _, _ ->
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_PROCEED to true))
            }.setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun copyEmailToClipboard(email: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AnkiWeb Email", email)
        clipboard.setPrimaryClip(clip)
        showSnackbar(R.string.email_copied)
    }
}
