/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.simulateDoubleTap
import com.ichi2.testutils.simulateUnconfirmedSingleTap
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test for [DeckPickerFloatingActionMenu]
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class DeckPickerFloatingActionMenuTest {

    @Mock private val deckPicker: DeckPicker = mock()

    @Mock private lateinit var mFabMain: FloatingActionButton

    @Mock private val mAddSharedLayout: LinearLayout = mock(defaultAnswer = Answers.RETURNS_MOCKS)

    @Mock private val mAddDeckLayout: LinearLayout = mock(defaultAnswer = Answers.RETURNS_MOCKS)

    @Mock private val mAddNoteLayout: LinearLayout = mock(defaultAnswer = Answers.RETURNS_MOCKS)

    @Mock private val mFabBGLayout: View = mock()

    @Mock private val mLinearLayout: LinearLayout = mock()

    @Mock private val mStudyOptionsFrame: View = mock()

    @Mock private lateinit var view: View

    @Mock private val addNoteButton: FloatingActionButton = mock()

    @Mock private val addSharedButton: FloatingActionButton = mock()

    @Mock private val addDeckButton: FloatingActionButton = mock()

    @Mock private val addNoteLabel: TextView = mock()

    @Mock private val addSharedLabel: TextView = mock()

    @Mock private val addDeckLabel: TextView = mock()

    @InjectMocks
    private lateinit var menu: DeckPickerFloatingActionMenu

    @Before
    fun before() {
        val ankiActivity = Robolectric.buildActivity(AnkiActivity::class.java, Intent()).get()
        ankiActivity.setTheme(R.style.Theme_Light_Compat)
        mFabMain = spy(FloatingActionButton(ankiActivity))

        // TODO: Figure out a nicer way of mocking
        view = mock {
            on { findViewById<FloatingActionButton>(R.id.fab_main) } doReturn mFabMain
            on { findViewById<LinearLayout>(R.id.add_shared_layout) } doReturn mAddSharedLayout
            on { findViewById<LinearLayout>(R.id.add_deck_layout) } doReturn mAddDeckLayout
            on { findViewById<View>(R.id.fabBGLayout) } doReturn mFabBGLayout
            on { findViewById<LinearLayout>(R.id.deckpicker_view) } doReturn mLinearLayout
            on { findViewById<View>(R.id.studyoptions_fragment) } doReturn mStudyOptionsFrame

            on { findViewById<FloatingActionButton>(R.id.add_shared_action) } doReturn addSharedButton
            on { findViewById<FloatingActionButton>(R.id.add_deck_action) } doReturn addDeckButton
            on { findViewById<TextView>(R.id.add_note_label) } doReturn addNoteLabel
            on { findViewById<TextView>(R.id.add_shared_label) } doReturn addSharedLabel
            on { findViewById<TextView>(R.id.add_deck_label) } doReturn addDeckLabel
        }
        menu = DeckPickerFloatingActionMenu(ApplicationProvider.getApplicationContext(), view, deckPicker)
    }

    @Test
    fun doubleTapAddsNote() {
        mFabMain.simulateDoubleTap()

        verify(deckPicker, times(1)).addNote()
    }

    @Test
    fun singleTapTogglesFab() {
        assertFalse("before a tap, menu should not be open") { menu.isFABOpen }

        mFabMain.simulateUnconfirmedSingleTap()

        assertTrue("after a tap, menu should be open") { menu.isFABOpen }

        mFabMain.simulateUnconfirmedSingleTap()
    }
}
