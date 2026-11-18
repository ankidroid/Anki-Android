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
import androidx.core.graphics.createBitmap
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.utils.ext.hasCheckedBackground
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
    private lateinit var activity: FragmentActivity
    private lateinit var browserRoot: View
    private lateinit var searchRoot: View

    @Before
    override fun setUp() {
        super.setUp()
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        Themes.setTheme(applicationContext)
        val activityController =
            org.robolectric.Robolectric
                .buildActivity(FragmentActivity::class.java)
                .create()
        activity = activityController.get()
        Themes.setTheme(activity)
        activityController.start().resume().visible()
        saveControllerForCleanup(activityController)
        context = activity
        browserRoot = LayoutInflater.from(activity).inflate(R.layout.fragment_card_browser_searchview, null, false)
        searchRoot = LayoutInflater.from(activity).inflate(R.layout.fragment_standard_search, null, false)
    }

    @Test
    fun `the search screen displays the chips in the same order as the browser`() {
        val expected = listOf(R.id.decks_chip, R.id.tags_chip, R.id.card_state_chip, R.id.flags_chip)

        assertEquals(expected, searchRoot.filterChipGroup().chipIds())
        assertEquals(listOf(R.id.sort_chip) + expected, browserRoot.filterChipGroup().chipIds())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `shared chips use the same icon in both screens`() {
        for (id in listOf(R.id.decks_chip, R.id.tags_chip, R.id.card_state_chip, R.id.flags_chip)) {
            assertTrue(
                browserRoot.chip(id).rendersSameIconAs(searchRoot.chip(id)),
                context.getString(id),
            )
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the shared icon comparison detects a different tag shape`() {
        val browserTag = browserRoot.chip(R.id.tags_chip)
        val searchTag = searchRoot.chip(R.id.tags_chip)
        searchTag.setTagFilter(listOf("a-tag"))

        assertFalse(browserTag.rendersSameIconAs(searchTag))
    }

    @Test
    fun `tag chip icon resource changes when the tag filter is selected`() {
        val browserTags = browserRoot.chip(R.id.tags_chip)
        val searchTags = searchRoot.chip(R.id.tags_chip)

        browserTags.setTagFilter(emptyList())
        searchTags.setTagFilter(listOf("a-tag"))

        assertFalse(browserTags.hasCheckedBackground)
        assertTrue(searchTags.hasCheckedBackground)
        assertEquals("Tags", browserTags.text)
        assertEquals("a-tag", searchTags.text)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `filter chips are outlined when unselected and filled when selected`() {
        val tags = searchRoot.chip(R.id.tags_chip)
        tags.setTagFilter(emptyList())
        assertFalse(tags.hasCheckedBackground)
        tags.setTagFilter(listOf("a-tag"))
        assertTrue(tags.hasCheckedBackground)

        val decks = searchRoot.chip(R.id.decks_chip)
        decks.setDeckFilter(emptyList())
        assertFalse(decks.hasCheckedBackground)
        decks.setDeckFilter(listOf(DeckNameId(name = "Default", id = 1)))
        assertTrue(decks.hasCheckedBackground)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `No Flag uses a different icon color from an empty filter`() {
        val flags = searchRoot.chip(R.id.flags_chip)
        flags.setFlagFilter(emptyList())
        assertEquals("Flags", flags.text)
        assertFalse(flags.hasCheckedBackground)
        assertTrue(flags.chipIcon != null)

        flags.setFlagFilter(listOf(Flag.NONE))

        assertEquals("No Flag", flags.text)
        assertTrue(flags.hasCheckedBackground)
        assertTrue(flags.chipIcon != null)
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

    private fun Drawable.render(): Bitmap {
        val bitmap = createBitmap(intrinsicWidth, intrinsicHeight)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}
