/*
 Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.themes

import android.app.UiModeManager
import android.content.Context
import androidx.annotation.StringDef
import androidx.core.content.ContextCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import timber.log.Timber

/**
 * Handles the user selectable themes
 * The user can choose one of the app themes or "Follow system" option
 * If one of the themes is selected, it will always be the theme used by the app
 * If "Follow system" is selected, the theme will be what is selected
 * on "Day" or "Night" theme categories, following the current system mode.
 */
object Themes {
    const val ALPHA_ICON_ENABLED_LIGHT = 255 // 100%
    const val ALPHA_ICON_DISABLED_LIGHT = 76 // 31%
    const val ALPHA_ICON_ENABLED_DARK = 138 // 54%

    // Themes preferences keys
    private const val APP_THEME_KEY = "appTheme"
    private const val DAY_THEME_KEY = "dayTheme"
    private const val NIGHT_THEME_KEY = "nightTheme"
    private const val NIGHT_MODE_PREFERENCE = "invertedColors"

    /* App themes values
     * These are unique values which be used to differentiate the themes,
     * following their values set on their ListPreference
     */
    const val FOLLOW_SYSTEM_MODE = "0"
    private const val APP_LIGHT_THEME = "1"
    const val APP_PLAIN_THEME = "2"
    private const val APP_BLACK_THEME = "3"
    const val APP_DARK_THEME = "4"

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @StringDef(APP_LIGHT_THEME, APP_PLAIN_THEME, APP_BLACK_THEME, APP_DARK_THEME)
    annotation class AppTheme

    /*
     * Follow system day and night themes.
     * Only used if "Follow system" is the selected app theme
     */
    // Day themes
    private const val DAY_LIGHT_THEME = "0"
    private const val DAY_PLAIN_THEME = "1"

    // Night themes
    private const val NIGHT_BLACK_THEME = "0"
    private const val NIGHT_DARK_THEME = "1"

    /**
     * Sets the current theme based on what is selected
     * by the user on the themes preference.
     * Also sets [NIGHT_MODE_PREFERENCE] if night mode is being used or not
     */
    @JvmStatic
    fun setTheme(context: Context) {
        val prefs = AnkiDroidApp.getSharedPrefs(context.applicationContext)
        if (themeFollowsSystem(context)) {
            if (systemIsInNightMode(context)) {
                when (prefs.getString(NIGHT_THEME_KEY, NIGHT_BLACK_THEME)) {
                    NIGHT_BLACK_THEME -> context.setTheme(R.style.Theme_Black_Compat)
                    NIGHT_DARK_THEME -> context.setTheme(R.style.Theme_Dark_Compat)
                }
                setNightModePreference(context, true)
            } else {
                when (prefs.getString(DAY_THEME_KEY, DAY_LIGHT_THEME)) {
                    DAY_LIGHT_THEME -> context.setTheme(R.style.Theme_Light_Compat)
                    DAY_PLAIN_THEME -> context.setTheme(R.style.Theme_Plain_Compat)
                }
                setNightModePreference(context, false)
            }
        } else {
            when (prefs.getString(APP_THEME_KEY, APP_LIGHT_THEME)) {
                APP_LIGHT_THEME -> { setNightModePreference(context, false); context.setTheme(R.style.Theme_Light_Compat) }
                APP_PLAIN_THEME -> { setNightModePreference(context, false); context.setTheme(R.style.Theme_Plain_Compat) }
                APP_BLACK_THEME -> { setNightModePreference(context, true); context.setTheme(R.style.Theme_Black_Compat) }
                APP_DARK_THEME -> { setNightModePreference(context, true); context.setTheme(R.style.Theme_Dark_Compat) }
            }
        }
        disableXiaomiForceDarkMode(context)
    }

    @JvmStatic
    fun setThemeLegacy(context: Context) {
        // we need the applicationContext to obtain preferences, we can't do this with a regular
        // Activity context since this is called before super.onCreate()
        val applicationContext = context.applicationContext
        val prefs = AnkiDroidApp.getSharedPrefs(applicationContext)
        if (themeFollowsSystem(applicationContext)) {
            if (systemIsInNightMode(applicationContext)) {
                when (prefs.getString(NIGHT_THEME_KEY, NIGHT_BLACK_THEME)) {
                    NIGHT_BLACK_THEME -> context.setTheme(R.style.LegacyActionBarBlack)
                    NIGHT_DARK_THEME -> context.setTheme(R.style.LegacyActionBarDark)
                }
            } else {
                when (prefs.getString(DAY_THEME_KEY, DAY_LIGHT_THEME)) {
                    DAY_LIGHT_THEME -> context.setTheme(R.style.LegacyActionBarLight)
                    DAY_PLAIN_THEME -> context.setTheme(R.style.LegacyActionBarPlain)
                }
            }
        } else {
            when (prefs.getString(APP_THEME_KEY, APP_LIGHT_THEME)) {
                APP_LIGHT_THEME -> context.setTheme(R.style.LegacyActionBarLight)
                APP_PLAIN_THEME -> context.setTheme(R.style.LegacyActionBarPlain)
                APP_BLACK_THEME -> context.setTheme(R.style.LegacyActionBarBlack)
                APP_DARK_THEME -> context.setTheme(R.style.LegacyActionBarDark)
            }
        }
        disableXiaomiForceDarkMode(context)
    }

    /**
     * #8150: Fix icons not appearing in Note Editor due to MIUI 12's "force dark" mode
     */
    @JvmStatic
    fun disableXiaomiForceDarkMode(context: Context) {
        // Setting a theme is an additive operation, so this adds a single property.
        context.setTheme(R.style.ThemeOverlay_Xiaomi)
    }

    @JvmStatic
    fun getResFromAttr(context: Context, resAttr: Int): Int {
        val attrs = intArrayOf(resAttr)
        return getResFromAttr(context, attrs)[0]
    }

    @JvmStatic
    fun getResFromAttr(context: Context, attrs: IntArray): IntArray {
        val ta = context.obtainStyledAttributes(attrs)
        for (i in attrs.indices) {
            attrs[i] = ta.getResourceId(i, 0)
        }
        ta.recycle()
        return attrs
    }

    @JvmStatic
    fun getColorFromAttr(context: Context?, colorAttr: Int): Int {
        val attrs = intArrayOf(colorAttr)
        return getColorFromAttr(context!!, attrs)[0]
    }

    @JvmStatic
    fun getColorFromAttr(context: Context, attrs: IntArray): IntArray {
        val ta = context.obtainStyledAttributes(attrs)
        for (i in attrs.indices) {
            attrs[i] = ta.getColor(i, ContextCompat.getColor(context, R.color.white))
        }
        ta.recycle()
        return attrs
    }

    /**
     * @return code of the theme being used.
     * Follow system Day and Night themes are exchanged
     * to their [AppTheme] equivalents so they can be differentiated by value
     */
    @JvmStatic
    @AppTheme
    fun getCurrentTheme(context: Context): String {
        val prefs = AnkiDroidApp.getSharedPrefs(context)
        if (themeFollowsSystem(context)) {
            if (systemIsInNightMode(context)) {
                when (prefs.getString(NIGHT_THEME_KEY, NIGHT_BLACK_THEME)) {
                    NIGHT_BLACK_THEME -> return APP_BLACK_THEME
                    NIGHT_DARK_THEME -> return APP_DARK_THEME
                }
            } else {
                when (prefs.getString(DAY_THEME_KEY, DAY_LIGHT_THEME)) {
                    DAY_LIGHT_THEME -> return APP_LIGHT_THEME
                    DAY_PLAIN_THEME -> return APP_PLAIN_THEME
                }
            }
        }
        return prefs.getString(APP_THEME_KEY, APP_LIGHT_THEME)!!
    }

    /**
     * @return if user system is in night mode
     */
    @JvmStatic
    fun systemIsInNightMode(context: Context): Boolean {
        val uiModeManager = ContextCompat.getSystemService(context, UiModeManager::class.java)
        if (uiModeManager != null) {
            return uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
        }
        Timber.w("Unable to getSystemService() - UIModeManager")
        return false
    }

    /**
     * Sets [NIGHT_MODE_PREFERENCE] to [isNightMode]
     */
    @JvmStatic
    fun setNightModePreference(context: Context, isNightMode: Boolean) {
        AnkiDroidApp.getSharedPrefs(context).edit().putBoolean(NIGHT_MODE_PREFERENCE, isNightMode).apply()
    }

    /**
     * @return if user current selected theme is "Follow system"
     */
    @JvmStatic
    fun themeFollowsSystem(context: Context): Boolean {
        val prefs = AnkiDroidApp.getSharedPrefs(context)
        val selectedAppTheme = prefs.getString(APP_THEME_KEY, FOLLOW_SYSTEM_MODE)
        return selectedAppTheme == FOLLOW_SYSTEM_MODE
    }
}
