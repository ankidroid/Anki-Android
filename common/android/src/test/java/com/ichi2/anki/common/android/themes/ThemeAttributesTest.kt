// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.android.themes

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.common.android.R
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Every theme attribute declared by `:common:android` must have a value in every app theme.
 *
 * An attribute that a theme doesn't set fails to resolve where it's used: Plain and E-ink inherit
 * Light, and Black inherits Dark, but Light and Dark don't share a parent.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeAttributesTest {
    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    /** Set by the styles applied to each answer button, not by the themes. */
    private val styleAttributes = setOf("answerButtonBackground")

    @Test
    fun `every theme attribute resolves in every app theme`() {
        // nonTransitiveRClass: this R only declares the attributes defined in :common:android
        val attributes = R.attr::class.java.fields.filter { it.name !in styleAttributes }

        val unresolved =
            (DayTheme.entries + NightTheme.entries).flatMap { theme ->
                val themed = ContextThemeWrapper(appContext, theme.styleResId).theme
                attributes
                    .filter { attribute ->
                        val value = TypedValue()
                        val resolved = themed.resolveAttribute(attribute.getInt(null), value, true)
                        !resolved || value.type == TypedValue.TYPE_ATTRIBUTE || value.type == TypedValue.TYPE_NULL
                    }.map { "${theme.name}: ${it.name}" }
            }

        assertEquals(emptyList(), unresolved, "theme attributes without a value")
    }
}
