// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.customstudy

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Exclude
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Include
import com.ichi2.themes.Themes
import org.junit.Test

class IncludedExcludedTagsAdapterScreenshotTest : ScreenshotTest() {
    @Test
    fun `selected tags remain readable`() {
        Themes.updateCurrentTheme(targetContext)
        val context = ContextThemeWrapper(targetContext, Themes.currentTheme.styleResId)

        val view =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(MaterialColors.getColor(context, android.R.attr.colorBackground, 0))
                addView(tagList(context, Include))
                addView(tagList(context, Exclude))
            }

        val width = 320.dp.toPx(context)
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, width, view.measuredHeight)

        captureView("selected_tags", view)
    }

    private fun tagList(
        context: Context,
        mode: TagsSelectionMode,
    ) = RecyclerView(context).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        layoutManager = LinearLayoutManager(context)
        adapter =
            IncludedExcludedTagsAdapter(context, mode).apply {
                isEnabled = true
                tags =
                    mutableListOf(
                        TagIncludedExcluded("selected", isIncluded = mode == Include, isExcluded = mode == Exclude),
                        TagIncludedExcluded("unselected"),
                    )
            }
    }
}
