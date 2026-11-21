// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.themes.Themes
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The browser shows filter chips in two places: above the card list and inside the
 * search screen. These tests check that both places show the same chips
 * (Issue 23298), and that a chip's icon shows whether the filter is on.
 */
@RunWith(AndroidJUnit4::class)
class ChipFiltersTest : RobolectricTest() {
    private lateinit var context: Context
    private lateinit var browserRoot: View
    private lateinit var searchRoot: View

    @Before
    override fun setUp() {
        super.setUp()
        context = ApplicationProvider.getApplicationContext()
        Themes.setTheme(context)
        browserRoot = LayoutInflater.from(context).inflate(R.layout.fragment_card_browser_searchview, null, false)
        searchRoot = LayoutInflater.from(context).inflate(R.layout.fragment_standard_search, null, false)
    }

    @Test
    fun `the search screen displays the chips in the same order as the browser`() {
        val expected = listOf(R.id.decks_chip, R.id.tags_chip, R.id.card_state_chip, R.id.flags_chip)

        assertEquals(expected, searchRoot.filterChipGroup().chipIds())
        assertEquals(listOf(R.id.sort_chip) + expected, browserRoot.filterChipGroup().chipIds())
    }

    @Test
    fun `shared chips use the same icon in both screens`() {
        for (id in listOf(R.id.decks_chip, R.id.tags_chip, R.id.card_state_chip, R.id.flags_chip)) {
            assertTrue(
                browserRoot.chip(id).rendersSameIconAs(searchRoot.chip(id)),
                context.getString(id),
            )
        }
    }

    @Test
    fun `tag chip icon resource changes when the tag filter is selected`() {
        val browserTags = browserRoot.chip(R.id.tags_chip)
        val searchTags = searchRoot.chip(R.id.tags_chip)

        browserTags.setTagFilter(emptyList())
        searchTags.setTagFilter(listOf("a-tag"))

        assertTrue(browserTags.usesIconResource(R.drawable.ic_label_outline), "browser: outlined tag icon")
        assertTrue(searchTags.usesIconResource(R.drawable.ic_tag), "search: filled tag icon")
        assertFalse(browserTags.usesSameIconResourceAs(searchTags))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `filter chips are outlined when unselected and filled when selected`() {
        val tags = searchRoot.chip(R.id.tags_chip)
        tags.setTagFilter(emptyList())
        assertTrue(tags.hasIcon(R.drawable.ic_label_outline), "tags: unselected")
        tags.setTagFilter(listOf("a-tag"))
        assertTrue(tags.hasIcon(R.drawable.ic_tag), "tags: selected")

        val decks = searchRoot.chip(R.id.decks_chip)
        decks.setDeckFilter(emptyList())
        assertTrue(decks.hasIcon(R.drawable.ic_cards_star), "decks: unselected")
        decks.setDeckFilter(listOf(DeckNameId(name = "Default", id = 1)))
        assertTrue(decks.hasIcon(R.drawable.ic_cards_star_filled), "decks: selected")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `No Flag uses a different icon color from an empty filter`() {
        val flags = searchRoot.chip(R.id.flags_chip)
        flags.setFlagFilter(emptyList())
        val emptyFilterIcon = flags.chipIcon!!.render()

        flags.setFlagFilter(listOf(Flag.NONE))

        assertFalse(emptyFilterIcon.sameAs(flags.chipIcon!!.render()))
    }

    @Test
    fun `in a right-to-left layout the tags chip icon keeps its direction when a tag is selected`() {
        RuntimeEnvironment.setQualifiers("+ldrtl")
        val rtlRoot = LayoutInflater.from(context).inflate(R.layout.fragment_card_browser_searchview, null, false)
        assertEquals(LAYOUT_DIRECTION_RTL, rtlRoot.resources.configuration.layoutDirection, "precondition: RTL")

        val tags = rtlRoot.chip(R.id.tags_chip)
        tags.setTagFilter(emptyList())
        assertTrue(tags.chipIcon!!.isAutoMirrored, "tags: unselected icon points to the start in RTL")
        tags.setTagFilter(listOf("a-tag"))
        assertTrue(tags.chipIcon!!.isAutoMirrored, "tags: selected icon points to the start in RTL")
    }

    /** The chip row of both screens sits inside the [R.id.chip_group] scroll view */
    private fun View.filterChipGroup(): ChipGroup = findViewById<ViewGroup>(R.id.chip_group).getChildAt(0) as ChipGroup

    private fun View.chip(id: Int): Chip = findViewById(id)

    private fun ChipGroup.chipIds(): List<Int> = (0 until childCount).map { getChildAt(it).id }

    /** Drawables can't be compared directly, so we draw the icons and compare the pictures */
    private fun Chip.rendersSameIconAs(other: Chip): Boolean = chipIcon?.render()?.sameAs(other.chipIcon?.render()) == true

    private fun Chip.hasIcon(
        @DrawableRes res: Int,
    ): Boolean {
        val expected = ContextCompat.getDrawable(context, res) ?: return chipIcon == null
        return chipIcon?.render()?.sameAs(expected.render()) == true
    }

    private fun Chip.usesIconResource(
        @DrawableRes res: Int,
    ): Boolean {
        val expected = ContextCompat.getDrawable(context, res) ?: return false
        return chipIcon?.constantState == expected.constantState
    }

    private fun Chip.usesSameIconResourceAs(other: Chip): Boolean = chipIcon?.constantState == other.chipIcon?.constantState

    private fun Drawable.render(): Bitmap {
        val bitmap = createBitmap(intrinsicWidth, intrinsicHeight)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}
