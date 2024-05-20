/*
 Copyright (c) 2024 Alexandre Ferreira <alexandre.bruno.ferreira@tecnico.ulisboa.pt>
 Copyright (c) 2024 Afonso Palmeira <afonsopalmeira@tecnico.ulisboa.pt>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import android.widget.Button
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.dialogs.flags.FlagsDialog
import com.ichi2.anki.dialogs.flags.FlagsDialogFactory
import com.ichi2.libanki.Card
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class FlagsDialogTest {

    @Mock
    private lateinit var mockReviewer: Reviewer

    @Mock
    private lateinit var mockCard: Card

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockReviewer.currentCard).thenReturn(mockCard)
    }

    @Test
    fun testFlagChange() {
        val args = FlagsDialog().withArguments(mockReviewer).arguments
        val scenario = FragmentScenario.launch(
            FlagsDialog::class.java,
            args,
            FlagsDialogFactory()
        )

        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onFragment { fragment ->
            fragment.view?.findViewById<Button>(R.id.action_flag_zero)?.performClick()
            assertEquals(0, mockCard.userFlag())
        }

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }
}
