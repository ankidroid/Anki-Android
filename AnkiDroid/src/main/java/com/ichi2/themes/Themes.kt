/*
 Copyright (c) 2011 Norbert Nagold <norbert.nagold></norbert.nagold>@gmail.com>
 Copyright (c) 2015 Timothy Rae <perceptualchaos2></perceptualchaos2>@gmail.com>
 Copyright (c) 2021 Akshay Jadhav <akshay0701></jadhavakshay0701>@gmail.com>

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
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R

object Themes {
    const val ALPHA_ICON_ENABLED_LIGHT = 255 // 100%
    const val ALPHA_ICON_DISABLED_LIGHT = 76 // 31%
    const val ALPHA_ICON_ENABLED_DARK = 138 // 54%

    /**
     * Preferences for the night theme mode
     */
    private const val DAY_THEME = "dayTheme"

    // Day themes
    const val THEME_DAY_LIGHT = 0
    const val THEME_DAY_PLAIN = 1

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(THEME_DAY_LIGHT, THEME_DAY_PLAIN)
    annotation class DAY_THEME_INTERFACE

    /**
     * Preferences for the night theme mode
     */
    private const val NIGHT_THEME = "nightTheme"

    // Night themes
    const val THEME_NIGHT_BLACK = 0
    const val THEME_NIGHT_DARK = 1

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(THEME_NIGHT_BLACK, THEME_NIGHT_DARK)
    annotation class NIGHT_THEME_INTERFACE

    @JvmStatic
    fun setTheme(context: Context) {
        val prefs = AnkiDroidApp.getSharedPrefs(context.applicationContext)
        if (prefs.getBoolean("invertedColors", false)) {
            val theme = prefs.getString(NIGHT_THEME, Integer.toString(THEME_NIGHT_BLACK))!!.toInt()
            when (theme) {
                THEME_NIGHT_DARK -> context.setTheme(R.style.Theme_Dark_Compat)
                THEME_NIGHT_BLACK -> context.setTheme(R.style.Theme_Black_Compat)
            }
        } else {
            val theme = prefs.getString(DAY_THEME, Integer.toString(THEME_DAY_LIGHT))!!.toInt()
            when (theme) {
                THEME_DAY_LIGHT -> context.setTheme(R.style.Theme_Light_Compat)
                THEME_DAY_PLAIN -> context.setTheme(R.style.Theme_Plain_Compat)
            }
        }
        disableXiaomiForceDarkMode(context)
    }

    @JvmStatic
    fun setThemeLegacy(context: Context) {
        val prefs = AnkiDroidApp.getSharedPrefs(context.applicationContext)
        if (prefs.getBoolean("invertedColors", false)) {
            val theme = prefs.getString(NIGHT_THEME, Integer.toString(THEME_NIGHT_BLACK))!!.toInt()
            when (theme) {
                THEME_NIGHT_DARK -> context.setTheme(R.style.LegacyActionBarDark)
                THEME_NIGHT_BLACK -> context.setTheme(R.style.LegacyActionBarBlack)
            }
        } else {
            val theme = prefs.getString(DAY_THEME, Integer.toString(THEME_DAY_LIGHT))!!.toInt()
            when (theme) {
                THEME_DAY_LIGHT -> context.setTheme(R.style.LegacyActionBarLight)
                THEME_DAY_PLAIN -> context.setTheme(R.style.LegacyActionBarPlain)
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
     * Return the current integer code of the theme being used, taking into account
     * whether we are in day mode or night mode.
     */
    @JvmStatic
    fun getCurrentTheme(context: Context?): Int {
        val prefs = AnkiDroidApp.getSharedPrefs(context)
        return if (prefs.getBoolean("invertedColors", false)) {
            prefs.getString(NIGHT_THEME, Integer.toString(THEME_NIGHT_BLACK))!!.toInt()
        } else {
            prefs.getString(DAY_THEME, Integer.toString(THEME_DAY_LIGHT))!!.toInt()
        }
    }
}
