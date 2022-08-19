package com.ichi2.anki.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R


class Settings(context: Context, preferences: SharedPreferences) :
        SettingRegistry(context, preferences) {

    /**********************************************************************************************/
    /***************************************** Appearance *****************************************/
    /**********************************************************************************************/

    enum class AnswerButtonsPosition(override val storedValue: String) : StoredValue {
        Top("top"),
        Bottom("bottom"),
        None("none"),
    }

    var answerButtonsPosition by enumSetting(R.string.answer_buttons_position_preference, AnswerButtonsPosition.Bottom)

    var centerCardContentsVertically by booleanSetting(R.string.center_vertically_preference, false)
}


val settings = run {
    val context = AnkiDroidApp.getInstance()
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    Settings(context, preferences)
}
