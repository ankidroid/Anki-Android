/****************************************************************************************
 * Copyright (c) 2022 lukstbit <lukstbit@users.noreply.github.com>                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.dialogs

import android.content.Intent
import android.net.Uri
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.RecursiveMenuItemAction.*
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.IntentUtil
import io.mockk.*
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecursiveMenuDialogTest {

    private lateinit var mockActivity: AnkiActivity

    @Before
    fun setUp() {
        mockActivity = mockk()
        launchFragment(
            fragmentArgs = bundleOf(
                RecursiveMenuDialog.KEY_MENU_ITEMS to testMenuItems,
                RecursiveMenuDialog.KEY_TITLE_RESOURCE_ID to R.string.help_item_report_bug
            ),
            themeResId = R.style.Theme_Light
        ) {
            MockRecursiveMenuDialog(mockActivity)
        }
    }

    @Test
    fun showsExpectedItemsAndOnlyThose() {
        onView(withText(R.string.help_item_report_bug)).check(matches(isDisplayed()))
        testMenuItems.forEach { menuItem ->
            if (menuItem.shouldBeVisible) {
                onView(withText(menuItem.titleResourceId)).check(matches(isDisplayed()))
            } else {
                onView(withText(menuItem.titleResourceId)).check(doesNotExist())
            }
        }
    }

    @Test
    fun showsExpectedChildMenuItemsOnSelection() {
        val dialogSlot = slot<RecursiveMenuDialog>()
        every { mockActivity.showDialogFragment(capture(dialogSlot)) } returns Unit
        // choose one menu item that we know has children
        val headerMenuItem = testMenuItems[0]
        onView(withText(headerMenuItem.titleResourceId)).perform(click())
        // get the children list of headerMenuItem and mark them as visible because we expect them
        // to be visible in the new dialog
        val expectedMenuItems = testMenuItems
            .filter { it.parentId == headerMenuItem.id }
            .map { it.copy(shouldBeVisible = true) } // mark this children as visible
        // new dialog to be shown has title of clicked item
        assertEquals(
            headerMenuItem.titleResourceId,
            dialogSlot.captured.arguments?.getInt(RecursiveMenuDialog.KEY_TITLE_RESOURCE_ID)
        )
        // test that the new dialog to be shown receives the expected title and list of menu items
        // as its arguments
        assertEquals(
            headerMenuItem.titleResourceId,
            dialogSlot.captured.arguments?.getInt(RecursiveMenuDialog.KEY_TITLE_RESOURCE_ID)
        )
        val actualSubDialogItems =
            dialogSlot.captured.arguments?.getParcelableArray(RecursiveMenuDialog.KEY_MENU_ITEMS)
                ?.map { it as RecursiveMenuItem }
                ?.filter { it.shouldBeVisible }
                ?.toTypedArray()
        assertArrayEquals(expectedMenuItems.toTypedArray(), actualSubDialogItems)
    }

    @Test
    fun opensUrlWhenSelectingProperMenuItem() {
        val uriSlot = slot<Uri>()
        every { mockActivity.openUrl(capture(uriSlot)) } returns Unit
        // the menu item with id=3 has a test url and it is the one we are clicking
        val menuItemWithUrl = testMenuItems.first { it.id == 3.toLong() }
        onView(withText(menuItemWithUrl.titleResourceId)).perform(click())
        assertEquals(Uri.parse(TEST_URL), uriSlot.captured)
    }

    @Test
    fun opensUrlResourceWhenSelectingProperMenuItem() {
        // the menu item with id=4 has a test url resource and it is the one we are clicking
        val menuItemWithUrl = testMenuItems.first { it.id == 4.toLong() }
        val uriSlot = slot<Uri>()
        every { mockActivity.getString(menuItemWithUrl.titleResourceId) } returns TEST_URL
        every { mockActivity.openUrl(capture(uriSlot)) } returns Unit
        onView(withText(menuItemWithUrl.titleResourceId)).perform(click())
        assertEquals(Uri.parse(TEST_URL), uriSlot.captured)
    }

    @Test
    fun startImporterSingleActionWhenSelectingProperMenuItem() {
        mockkObject(ImportFileSelectionFragment)
        // try for single file
        every { ImportFileSelectionFragment.openImportFilePicker(mockActivity, false) } returns Unit
        // the menu item with id=5 is for Importer(false) option and it is the one we are clicking
        val menuItemWithUrl = testMenuItems.first { it.id == 5.toLong() }
        onView(withText(menuItemWithUrl.titleResourceId)).perform(click())
        verify(exactly = 1) {
            ImportFileSelectionFragment.openImportFilePicker(
                mockActivity,
                false
            )
        }
        unmockkObject(ImportFileSelectionFragment)
    }

    @Test
    fun startImporterMultipleActionWhenSelectingProperMenuItem() {
        mockkObject(ImportFileSelectionFragment)
        // try for multiple files
        every { ImportFileSelectionFragment.openImportFilePicker(mockActivity, true) } returns Unit
        // the menu item with id=6 is for Importer(true) option and it is the one we are clicking
        val menuItemWithUrl = testMenuItems.first { it.id == 6.toLong() }
        onView(withText(menuItemWithUrl.titleResourceId)).perform(click())
        verify(exactly = 1) { ImportFileSelectionFragment.openImportFilePicker(mockActivity, true) }
        unmockkObject(ImportFileSelectionFragment)
    }

    @Test
    fun startsRateActionWhenSelectingProperMenuItem() {
        mockkStatic(AnkiDroidApp::class)
        mockkStatic(IntentUtil::tryOpenIntent)
        every { IntentUtil.tryOpenIntent(mockActivity, any()) } returns Unit
        every { AnkiDroidApp.getMarketIntent(any()) } returns Intent()
        // the menu item with id=7 is for Rate option and it is the one we are clicking
        val menuItemWithUrl = testMenuItems.first { it.id == 7.toLong() }
        onView(withText(menuItemWithUrl.titleResourceId)).perform(click())
        verify(exactly = 1) { IntentUtil.tryOpenIntent(mockActivity, any()) }
        unmockkStatic(IntentUtil::tryOpenIntent)
        unmockkStatic(AnkiDroidApp::class)
    }

    @Test
    fun reportsErrorWhenSelectingProperMenuItem() {
        mockkStatic(AdaptionUtil::isUserATestClient)
        mockkStatic(CrashReportService::sendReport)
        // make sure the environment is not considered a test client
        every { AdaptionUtil.isUserATestClient } returns false
        // send the error report successfully
        every { CrashReportService.sendReport(mockActivity) } returns true
        // the menu item with id=8 is for Send Error report option and it is the one we are clicking
        val menuItemWithUrl = testMenuItems.first { it.id == 8.toLong() }
        onView(withText(menuItemWithUrl.titleResourceId)).perform(click())
        verify(exactly = 1) { CrashReportService.sendReport(mockActivity) }
        unmockkStatic(CrashReportService::sendReport)
        unmockkStatic(AdaptionUtil::isUserATestClient)
    }

    companion object {
        private const val TEST_URL = "https://github.com/ankidroid/Anki-Android"
        private val testMenuItems = arrayOf(
            // this group is used for testing parent-child relation
            RecursiveMenuItem(R.string.help_title_community, R.drawable.ic_help_black, "analyticsId1", 1, null, true, Header),
            RecursiveMenuItem(R.string.help_item_twitter, R.drawable.ic_help_black, "analyticsId10", 10, 1, false, OpenUrlResource(R.string.app_name)),

            RecursiveMenuItem(R.string.help_item_anki_manual, R.drawable.ic_help_black, "analyticsId2", 2, null, true, Header),
            RecursiveMenuItem(R.string.help_item_ankidroid_manual, R.drawable.ic_help_black, "analyticsId1", 20, 2, false, OpenUrlResource(R.string.app_name)),
            // used to test opening a url
            RecursiveMenuItem(R.string.help_item_facebook, R.drawable.ic_help_black, "analyticsId3", 3, null, true, OpenUrl(TEST_URL)),
            // used to test opening a url string resource
            RecursiveMenuItem(R.string.help_item_discord, R.drawable.ic_help_black, "analyticsId4", 4, null, true, OpenUrlResource(R.string.help_item_discord)),
            // used to test importer(false)
            RecursiveMenuItem(R.string.import_title, R.drawable.ic_help_black, "analyticsId5", 5, null, true, Importer()),
            // used to test importer(true)
            RecursiveMenuItem(R.string.about_title, R.drawable.ic_help_black, "analyticsId5", 6, null, true, Importer(true)),
            // used to test rating the app
            RecursiveMenuItem(R.string.help_item_support_rate_ankidroid, R.drawable.ic_help_black, "analyticsId6", 7, null, true, Rate),
            // used to test reporting an error
            RecursiveMenuItem(R.string.help_title_send_exception, R.drawable.ic_help_black, "analyticsId7", 8, null, true, ReportError),
        )

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            unmockkAll()
        }
    }

    class MockRecursiveMenuDialog(private val mockActivity: AnkiActivity) : RecursiveMenuDialog() {

        override fun getActivityDelegate(): AnkiActivity = mockActivity
    }
}
