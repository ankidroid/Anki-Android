/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.content.Context
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.LanguageUtils
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import org.intellij.lang.annotations.Language

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` and `ankidroid.js` are added
 * * `bridgeCommand()` is ignored
 *
 * Aimed to be used only for reviewing/previewing cards
 */
fun stdHtml(
    context: Context = AnkiDroidApp.instance,
    nightMode: Boolean = false
): String {
    val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"

    val baseTheme: String
    val docClass: String
    if (nightMode) {
        docClass = "night-mode"
        baseTheme = "dark"
    } else {
        docClass = ""
        baseTheme = "light"
    }

    val colors = if (!nightMode) {
        val canvasColor = MaterialColors.getColor(
            context,
            android.R.attr.colorBackground,
            android.R.color.white
        ).toRGBHex()
        val fgColor =
            MaterialColors.getColor(context, android.R.attr.textColor, android.R.color.black).toRGBHex()
        ":root { --canvas: $canvasColor ; --fg: $fgColor; }"
    } else {
        val canvasColor = MaterialColors.getColor(
            context,
            android.R.attr.colorBackground,
            android.R.color.black
        ).toRGBHex()
        val fgColor =
            MaterialColors.getColor(context, android.R.attr.textColor, android.R.color.white).toRGBHex()
        ":root[class*=night-mode] { --canvas: $canvasColor; --fg: $fgColor; }"
    }

    @Language("HTML")
    val html = """
                <!DOCTYPE html>
                <html class="$docClass" dir="$languageDirectionality" data-bs-theme="$baseTheme">
                <head>
                    <title>AnkiDroid</title>
                        <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/root-vars.css">
                        <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer.css">
                        <link rel="stylesheet" type="text/css" href="file:///android_asset/ankidroid.css">
                    <style>
                        .night-mode button { --canvas: #606060; --fg: #eee; }
                        $colors
                    </style>
                </head>
                <body class="${bodyClass()}">
                    <div id="_mark" hidden>&#x2605;</div>
                    <div id="_flag" hidden>&#x2691;</div>
                    <div id="qa"></div>
                    <script src="file:///android_asset/backend/js/jquery.min.js"></script>
                    <script src="file:///android_asset/backend/js/mathjax.js"></script>
                    <script src="file:///android_asset/backend/js/vendor/mathjax/tex-chtml-full.js"></script>
                    <script src="file:///android_asset/scripts/ankidroid.js"></script>
                    <script src="file:///android_asset/backend/js/reviewer.js"></script>
                    <script>bridgeCommand = function(){};</script>
                </body>
                </html>
    """.trimIndent()
    return html
}

/** @return body classes used when showing a card */
fun bodyClassForCardOrd(
    cardOrd: Int,
    nightMode: Boolean = Themes.currentTheme.isNightMode
): String {
    return "card card${cardOrd + 1} ${bodyClass(nightMode)}"
}

private fun bodyClass(nightMode: Boolean = Themes.currentTheme.isNightMode): String {
    return if (nightMode) "nightMode night_mode" else ""
}
