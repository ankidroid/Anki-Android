// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.preference.ListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.ichi2.anki.databinding.DialogFetchMediaPreferenceBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia

class FetchMediaPreference(
    context: Context,
    attrs: AttributeSet?,
) : ListPreference(context, attrs),
    DialogFragmentProvider {
    override fun makeDialogFragment() = FetchMediaPreferenceDialogFragment()
}

class FetchMediaPreferenceDialogFragment : PreferenceDialogFragmentCompat() {
    @VisibleForTesting
    internal lateinit var binding: DialogFetchMediaPreferenceBinding

    private val ShouldFetchMedia.button: RadioButton
        get() =
            when (this) {
                ShouldFetchMedia.ALWAYS -> binding.fetchMediaAlways
                ShouldFetchMedia.ONLY_UNMETERED -> binding.fetchMediaOnlyUnmetered
                ShouldFetchMedia.NEVER -> binding.fetchMediaNever
            }

    override fun onCreateDialogView(context: Context): View {
        binding = DialogFetchMediaPreferenceBinding.inflate(LayoutInflater.from(context))
        return binding.root
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        Prefs.shouldFetchMedia.button.isChecked = true
        binding.missingMediaWarningSwitch.isChecked = Prefs.warnAboutMissingMedia
        binding.fetchMediaOptions.setOnCheckedChangeListener { _, _ -> updateWarningPanel() }
        updateWarningPanel()
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return
        val choice = ShouldFetchMedia.entries.firstOrNull { it.button.isChecked } ?: return
        Prefs.warnAboutMissingMedia = binding.missingMediaWarningSwitch.isChecked
        val value = getString(choice.entryResId)
        if (preference.callChangeListener(value)) {
            (preference as FetchMediaPreference).value = value
        }
    }

    private fun updateWarningPanel() {
        binding.mediaSyncOffPanel.isVisible = binding.fetchMediaNever.isChecked
    }
}
