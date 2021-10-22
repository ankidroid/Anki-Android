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

/** Responsible for calculating CSS and element styles and modifying content on a flashcard  */
class CardAppearance(private val customFonts: ReviewerCustomFonts, private val cardZoom: Int, private val imageZoom: Int, val isNightMode: Boolean, private val centerVertically: Boolean) {
    /**
     * hasUserDefinedNightMode finds out if the user has included class .night_mode in card's stylesheet
     */
    fun hasUserDefinedNightMode(card: Card): Boolean {
        // TODO: find more robust solution that won't match unrelated classes like "night_mode_old"
        return card.css().contains(".night_mode") || card.css().contains(".nightMode")
    }

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
    fun getCssClasses(currentTheme: Int): String {
        val cardClass = StringBuilder()
        if (centerVertically) {
            cardClass.append(" vertically_centered")
        }
        if (isNightMode) {
            // Enable the night-mode class
            cardClass.append(" night_mode nightMode")

            // Emit the dark_mode selector to allow dark theme overrides
            if (currentTheme == Themes.THEME_NIGHT_DARK) {
                cardClass.append(" ankidroid_dark_mode")
            }
        } else {
            // Emit the plain_mode selector to allow plain theme overrides
            if (currentTheme == Themes.THEME_DAY_PLAIN) {
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

    fun getCardClass(oneBasedCardOrdinal: Int, currentTheme: Int): String {
        var cardClass = "card card$oneBasedCardOrdinal"
        cardClass += getCssClasses(currentTheme)
        return cardClass
    }

    companion object {
        /** Constant for class attribute signaling answer  */
        const val ANSWER_CLASS = "\"answer\""

        /** Constant for class attribute signaling question  */
        const val QUESTION_CLASS = "\"question\""
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
    }
}
