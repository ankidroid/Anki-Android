/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import androidx.annotation.CheckResult
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.libanki.Card
import com.ichi2.themes.Themes
import com.ichi2.themes.Themes.AppTheme

/** Responsible for calculating CSS and element styles and modifying content on a flashcard  */
class CardAppearance(private val customFonts: ReviewerCustomFonts, private val cardZoom: Int, private val imageZoom: Int, val isNightMode: Boolean, private val centerVertically: Boolean) {
    /** Below could be in a better abstraction.  */
    fun appendCssStyle(style: StringBuilder) {
        // Zoom cards
        if (cardZoom != 100) {
            style.append(String.format("body { zoom: %s }\n", cardZoom / 100.0))
        }

        // Zoom images
        if (imageZoom != 100) {
            style.append(String.format("img { zoom: %s }\n", imageZoom / 100.0))
        }
    }

    @CheckResult
    fun getCssClasses(@AppTheme currentTheme: String): String {
        val cardClass = StringBuilder()
        if (centerVertically) {
            cardClass.append(" vertically_centered")
        }
        if (isNightMode) {
            // Enable the night-mode class
            cardClass.append(" night_mode nightMode")

            // Emit the dark_mode selector to allow dark theme overrides
            if (currentTheme == Themes.APP_DARK_THEME) {
                cardClass.append(" ankidroid_dark_mode")
            }
        } else {
            // Emit the plain_mode selector to allow plain theme overrides
            if (currentTheme == Themes.APP_PLAIN_THEME) {
                cardClass.append(" ankidroid_plain_mode")
            }
        }
        return cardClass.toString()
    }

    val style: String
        get() {
            val style = StringBuilder()
            customFonts.updateCssStyle(style)
            appendCssStyle(style)
            return style.toString()
        }

    fun getCardClass(oneBasedCardOrdinal: Int, @AppTheme currentTheme: String): String {
        var cardClass = "card card$oneBasedCardOrdinal"
        cardClass += getCssClasses(currentTheme)
        return cardClass
    }

    companion object {
        private val nightModeClassRegex = Regex("\\.night(?:_m|M)ode\\b")

        @JvmStatic
        fun create(customFonts: ReviewerCustomFonts, preferences: SharedPreferences): CardAppearance {
            val cardZoom = preferences.getInt("cardZoom", 100)
            val imageZoom = preferences.getInt("imageZoom", 100)
            val nightMode = isInNightMode(preferences)
            val centerVertically = preferences.getBoolean("centerVertically", false)
            return CardAppearance(customFonts, cardZoom, imageZoom, nightMode, centerVertically)
        }

        @JvmStatic
        fun isInNightMode(sharedPrefs: SharedPreferences): Boolean {
            return sharedPrefs.getBoolean("invertedColors", false)
        }

        @JvmStatic
        fun fixBoldStyle(content: String): String {
            // In order to display the bold style correctly, we have to change
            // font-weight to 700
            return content.replace("font-weight:600;", "font-weight:700;")
        }
        /**
         * hasUserDefinedNightMode finds out if the user has included class .night_mode in card's stylesheet
         */
        fun hasUserDefinedNightMode(card: Card): Boolean {
            return card.css().contains(nightModeClassRegex)
        }
    }
}
