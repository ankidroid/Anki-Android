// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.customstudy

import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class IncludedExcludedTagsAdapterTest : RobolectricTest() {
    @Test
    fun `selected E-ink tags remain readable in day mode`() {
        assertSelectedTagsReadable("+notnight")
    }

    @Test
    fun `selected E-ink tags remain readable in night mode`() {
        assertSelectedTagsReadable("+night")
    }

    private fun assertSelectedTagsReadable(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val context = ContextThemeWrapper(targetContext, CommonR.style.Theme_AnkiDroid_Eink)
        for (mode in IncludedExcludedTagsAdapter.TagsSelectionMode.entries) {
            val adapter =
                IncludedExcludedTagsAdapter(context, mode).apply {
                    isEnabled = true
                    tags = mutableListOf(TagIncludedExcluded("exam"))
                }
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            val label = holder.tagView
            val originalTextColors = label.textColors
            label.performClick()
            adapter.onBindViewHolder(holder, 0)
            val background = (label.background as ColorDrawable).color
            val contrast = ColorUtils.calculateContrast(label.currentTextColor, background)
            assertTrue(contrast >= 4.5, "Selected $mode tag contrast was $contrast in $qualifiers")
            label.performClick()
            adapter.onBindViewHolder(holder, 0)
            assertEquals(originalTextColors, label.textColors, "Deselected $mode tag must restore its text colors")
        }
    }
}
