/*
 * Copyright (c) 2025 AnkiDroid contributors
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 */
package com.ichi2.anki.pages

import android.content.Context
import android.webkit.WebView
import androidx.appcompat.widget.ThemeUtils
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex

/**
 * Overrides Anki desktop web UI CSS variables (SvelteKit) so embedded pages match
 * the AnkiDroid AMOLED design system instead of the default gray night palette.
 */
object AnkiPageThemeInjector {
    /**
     * Replaces `--canvas*` / `--fg*` tokens used by statistics, deck options, etc.
     */
    fun applyNightModeOverrides(webView: WebView) {
        if (!Themes.isNightTheme) return
        val script = buildInjectionScript(webView.context)
        webView.evaluateAfterDOMContentLoaded(script)
        // Svelte may hydrate after DOMContentLoaded; re-apply once content is painted.
        webView.postDelayed({ webView.evaluateJavascript(script, null) }, 450)
    }

    fun buildInjectionScript(context: Context): String {
        val canvas = color(context, R.attr.adsBackground)
        val elevated = color(context, R.attr.adsSurfaceElevated)
        val inset = color(context, R.attr.adsSurface)
        val fg = color(context, R.attr.adsForeground)
        val fgSubtle = color(context, R.attr.adsMutedForeground)
        val border = color(context, R.attr.adsBorder)
        val accent = color(context, R.attr.adsAccent)

        val cssVariables =
            """
            --canvas: $canvas !important;
            --canvas-elevated: $elevated !important;
            --canvas-inset: $inset !important;
            --canvas-overlay: $elevated !important;
            --canvas-code: $inset !important;
            --canvas-glass: ${elevated}66 !important;
            --fg: $fg !important;
            --fg-subtle: $fgSubtle !important;
            --fg-disabled: $fgSubtle !important;
            --fg-faint: $fgSubtle !important;
            --fg-link: $accent !important;
            --border: $border !important;
            --border-subtle: $border !important;
            --border-strong: $border !important;
            --border-focus: $accent !important;
            --button-bg: $elevated !important;
            --button-gradient-start: $elevated !important;
            --button-gradient-end: $inset !important;
            --button-hover-border: $border !important;
            --button-disabled: ${elevated}80 !important;
            --scrollbar-bg: $border !important;
            --scrollbar-bg-hover: $fgSubtle !important;
            --scrollbar-bg-active: $fg !important;
            --shadow: #000000 !important;
            --shadow-inset: $border !important;
            --shadow-subtle: $elevated !important;
            """.trimIndent()

        return """
            (function ankidroidApplyAdsTheme() {
                var styleId = 'ankidroid-ads-theme';
                var style = document.getElementById(styleId);
                if (!style) {
                    style = document.createElement('style');
                    style.id = styleId;
                    document.head.appendChild(style);
                }
                style.textContent = `
                  :root.night-mode, html.night-mode, .night-mode {
                    $cssVariables
                  }
                  html, body {
                    background-color: $canvas !important;
                    color: $fg !important;
                  }
                  .range-box {
                    background: $canvas !important;
                    border-bottom-color: $border !important;
                  }
                `;
                document.documentElement.classList.add('night-mode');
                if (document.body) {
                    document.body.style.setProperty('background-color', '$canvas', 'important');
                    document.body.style.setProperty('color', '$fg', 'important');
                }
            })();
            """.trimIndent()
    }

    private fun color(
        context: Context,
        attr: Int,
    ): String = ThemeUtils.getThemeAttrColor(context, attr).toRGBHex()
}
