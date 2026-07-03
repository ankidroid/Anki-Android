package com.ichi2.anki.preferences

import com.ichi2.anki.R

class FmgeSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_fmge

    override fun initSubscreen() {
        requireActivity().title = getString(R.string.fmge_settings_title)
    }

    override val analyticsScreenNameConstant: String
        get() = "prefs.fmge"
}
