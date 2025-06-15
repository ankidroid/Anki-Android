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
import androidx.appcompat.widget.ThemeUtils
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.Flag
import com.ichi2.anki.LanguageUtils
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import org.intellij.lang.annotations.Language

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` is added
 * * `bridgeCommand()` is ignored
 * * flag colors are overridden
 *
 * Aimed to be used only for reviewing/previewing cards
 *
 * @param extraJsAssets paths of additional Javascript assets
 * in the `android_assets` folder to be included
 */
@Language("HTML")
fun stdHtml(
    context: Context = AnkiDroidApp.instance,
    extraJsAssets: List<String> = emptyList(),
    nightMode: Boolean = false,
): String {
    val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"
    val baseTheme = if (nightMode) "dark" else "light"
    val docClass = if (nightMode) "night-mode" else ""
    val rootNightMode = if (nightMode) "[class*=night-mode]" else ""

    val canvasColor = ThemeUtils.getThemeAttrColor(context, android.R.attr.colorBackground).toRGBHex()
    val fgColor = ThemeUtils.getThemeAttrColor(context, android.R.attr.textColor).toRGBHex()
    val flagColors = getFlagColors(context)
    val colors = ":root$rootNightMode { --canvas: $canvasColor; --fg: $fgColor; $flagColors }"

    val jsAssets: List<String> =
        listOf(
            "backend/js/jquery.min.js",
            "backend/js/mathjax.js",
            "backend/js/vendor/mathjax/tex-chtml-full.js",
            "backend/js/reviewer.js",
        ) + extraJsAssets
    val jsTxt =
        jsAssets.joinToString("\n") {
            """<script src="file:///android_asset/$it"></script>"""
        }

    return """
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
            <div id="_mark" hidden><svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="#FFF" stroke="#000" stroke-width="36" viewBox="0 -960 960 960"><path d="m233-120 65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Z"/></svg></div>
            <div id="_flag" hidden><svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><path fill="currentColor" d="M14.4 6 14 4H5v17h2v-7h5.6l.4 2h7V6z"/></svg></div>
            <div id="qa"></div>
            $jsTxt
            <script>bridgeCommand = function(){};</script>
        </body>
        </html>
        """.trimIndent()
}

/** @return body classes used when showing a card */
fun bodyClassForCardOrd(
    cardOrd: Int,
    nightMode: Boolean = Themes.currentTheme.isNightMode,
): String = "card card${cardOrd + 1} ${bodyClass(nightMode)}"

private fun bodyClass(nightMode: Boolean = Themes.currentTheme.isNightMode): String = if (nightMode) "nightMode night_mode" else ""

@Language("CSS")
private fun getFlagColors(context: Context): String {
    val flags = Flag.entries.filter { it.studyScreenColorRes != null }
    return flags.joinToString(separator = " ") { flag ->
        val color = context.getColor(flag.studyScreenColorRes!!)
        val hex = color.toRGBHex()
        "--flag-${flag.code}: $hex;"
    }
}
