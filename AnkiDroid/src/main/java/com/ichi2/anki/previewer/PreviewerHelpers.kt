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

import android.R
import android.content.Context
import android.content.Intent
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.NoteEditor
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import org.intellij.lang.annotations.Language

class NoteEditorDestination(val cardId: Long) {
    fun toIntent(context: Context): Intent =
        Intent(context, NoteEditor::class.java).apply {
            putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_PREVIEWER_EDIT)
            putExtra(NoteEditor.EXTRA_EDIT_FROM_CARD_ID, cardId)
        }
}

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` is added
 * * `js-api.js` is added
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
            R.attr.colorBackground,
            R.color.white
        ).toRGBHex()
        val fgColor =
            MaterialColors.getColor(context, R.attr.textColor, R.color.black).toRGBHex()
        ":root { --canvas: $canvasColor ; --fg: $fgColor; }"
    } else {
        val canvasColor = MaterialColors.getColor(
            context,
            R.attr.colorBackground,
            R.color.black
        ).toRGBHex()
        val fgColor =
            MaterialColors.getColor(context, R.attr.textColor, R.color.white).toRGBHex()
        ":root[class*=night-mode] { --canvas: $canvasColor; --fg: $fgColor; }"
    }

    @Suppress("UnnecessaryVariable") // necessary for the HTML notation
    @Language("HTML")
    val html = """
                <!DOCTYPE html>
                <html class="$docClass" dir="$languageDirectionality" data-bs-theme="$baseTheme">
                <head>
                    <title>AnkiDroid</title>
                        <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/web/root-vars.css">
                        <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/web/reviewer.css">
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
                    <script src="file:///android_asset/jquery.min.js"></script>
                    <script src="file:///android_asset/mathjax/tex-chtml.js"></script>
                    <script src="file:///android_asset/backend/web/reviewer.js"></script>
                    <script src="file:///android_asset/scripts/js-api.js"></script>
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
