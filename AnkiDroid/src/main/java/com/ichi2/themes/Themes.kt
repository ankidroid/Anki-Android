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

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R

/**
 * Helper methods to configure things related to AnkiDroid's themes
 */
object Themes {
    const val ALPHA_ICON_ENABLED_LIGHT = 255 // 100%
    const val ALPHA_ICON_DISABLED_LIGHT = 76 // 31%

    const val FOLLOW_SYSTEM_MODE = "0"
    private const val APP_THEME_KEY = "appTheme"
    private const val DAY_THEME_KEY = "dayTheme"
    private const val NIGHT_THEME_KEY = "nightTheme"

    var currentTheme: Theme = Theme.fallback
    var systemIsInNightMode: Boolean = false

    /**
     * Sets theme to [currentTheme]
     */
    fun setTheme(context: Context) {
        context.setTheme(currentTheme.resId)
    }

    fun setLegacyActionBar(context: Context) {
        context.setTheme(R.style.ThemeOverlay_LegacyActionBar)
    }

    /**
     * Updates [currentTheme] value based on preferences.
     * If `Follow system` is selected, it's updated to the theme set
     * on `Day` or `Night` theme according to system's current mode
     * Otherwise, updates to the selected theme.
     */
    fun updateCurrentTheme() {
        val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance.applicationContext)

        currentTheme = if (themeFollowsSystem()) {
            if (systemIsInNightMode) {
                Theme.ofId(prefs.getString(NIGHT_THEME_KEY, Theme.BLACK.id)!!)
            } else {
                Theme.ofId(prefs.getString(DAY_THEME_KEY, Theme.LIGHT.id)!!)
            }
        } else {
            Theme.ofId(prefs.getString(APP_THEME_KEY, Theme.fallback.id)!!)
        }
    }

    enum class ThemeChanged { Yes, No }

    fun updateCurrentThemeByUiMode(uiMode: Int): ThemeChanged {
        val systemIsInNightMode =
            uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        if (Themes.systemIsInNightMode != systemIsInNightMode) {
            Themes.systemIsInNightMode = systemIsInNightMode
            if (themeFollowsSystem()) {
                updateCurrentTheme()
                return ThemeChanged.Yes
            }
        }

        return ThemeChanged.No
    }

    /**
     * #8150: Fix icons not appearing in Note Editor due to MIUI 12's "force dark" mode
     */
    fun disableXiaomiForceDarkMode(context: Context) {
        // Setting a theme is an additive operation, so this adds a single property.
        context.setTheme(R.style.ThemeOverlay_Xiaomi)
    }

    fun getResFromAttr(context: Context, resAttr: Int): Int {
        val attrs = intArrayOf(resAttr)
        return getResFromAttr(context, attrs)[0]
    }

    fun getResFromAttr(context: Context, attrs: IntArray): IntArray {
        val ta = context.obtainStyledAttributes(attrs)
        for (i in attrs.indices) {
            attrs[i] = ta.getResourceId(i, 0)
        }
        ta.recycle()
        return attrs
    }

    @JvmStatic // tests failed when removing, maybe try later
    @ColorInt
    fun getColorFromAttr(context: Context?, colorAttr: Int): Int {
        val attrs = intArrayOf(colorAttr)
        return getColorFromAttr(context!!, attrs)[0]
    }

    @JvmStatic // tests failed when removing, maybe try later
    @ColorInt
    fun getColorFromAttr(context: Context, attrs: IntArray): IntArray {
        val ta = context.obtainStyledAttributes(attrs)
        for (i in attrs.indices) {
            attrs[i] = ta.getColor(i, ContextCompat.getColor(context, R.color.white))
        }
        ta.recycle()
        return attrs
    }

    /**
     * @return required color depending on the theme from the given attribute
     */
    @ColorInt
    fun Fragment.getColorFromAttr(@AttrRes attribute: Int): Int {
        return getColorFromAttr(requireContext(), attribute)
    }

    /**
     * @return if current selected theme is `Follow system`
     */
    fun themeFollowsSystem(): Boolean {
        val prefs = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.instance.applicationContext)
        return prefs.getString(APP_THEME_KEY, FOLLOW_SYSTEM_MODE) == FOLLOW_SYSTEM_MODE
    }
}
