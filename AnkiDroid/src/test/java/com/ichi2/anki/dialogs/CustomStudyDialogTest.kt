/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.libanki.Collection
import com.ichi2.libanki.sched.Scheduler
import com.ichi2.testutils.ParametersUtils
import com.ichi2.testutils.isJsonEqual
import com.ichi2.testutils.items
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.hamcrest.core.IsNull
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class CustomStudyDialogTest : RobolectricTest() {
    private var mockListener: CustomStudyListener? = null
    override fun setUp() {
        super.setUp()
        mockListener = Mockito.mock(CustomStudyListener::class.java)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        Mockito.reset(mockListener)
    }

    @Test
    fun learnAheadCardsRegressionTest() {
        // #6289 - Regression Test
        val args = CustomStudyDialog(mock(), ParametersUtils.whatever())
            .withArguments(CustomStudyDialog.ContextMenuOption.STUDY_AHEAD, 1)
            .arguments
        val factory = CustomStudyDialogFactory({ this.col }, mockListener)
        val scenario = FragmentScenario.launch(CustomStudyDialog::class.java, args, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: CustomStudyDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            dialog!!.getActionButton(WhichButton.POSITIVE).callOnClick()
        }
        val customStudy = col.decks.current()
        MatcherAssert.assertThat("Custom Study should be dynamic", customStudy.isFiltered)
        MatcherAssert.assertThat("could not find deck: Custom study session", customStudy, notNullValue())
        customStudy.remove("id")
        customStudy.remove("mod")
        customStudy.remove("name")
        val expected = "{" +
            "\"browserCollapsed\":false," +
            "\"collapsed\":false," +
            "\"delays\":null," +
            "\"desc\":\"\"," +
            "\"dyn\":1," +
            "\"lrnToday\":[0,0]," +
            "\"newToday\":[0,0]," +
            "\"previewDelay\":0," +
            "\"previewAgainSecs\":60,\"previewHardSecs\":600,\"previewGoodSecs\":0," +
            "\"resched\":true," +
            "\"revToday\":[0,0]," +
            "\"separate\":true," +
            "\"terms\":[[\"deck:\\\"Default\\\" prop:due<=1\",99999,6]]," +
            "\"timeToday\":[0,0]," +
            "\"usn\":-1" +
            "}"
        MatcherAssert.assertThat(customStudy, isJsonEqual(JSONObject(expected)))
    }

    @Test
    @Config(qualifiers = "en")
    @KotlinCleanup("Use kotlin based Mockito extensions")
    fun increaseNewCardLimitRegressionTest() {
        // #8338 - Regression Test
        val args = CustomStudyDialog(mock(), ParametersUtils.whatever())
            .withArguments(CustomStudyDialog.ContextMenuConfiguration.STANDARD, 1)
            .arguments

        // we are using mock collection for the CustomStudyDialog but still other parts of the code
        // access a real collection, so we must ensure that collection is loaded first
        // so we don't get net/ankiweb/rsdroid/BackendException$BackendDbException$BackendDbLockedException
        ensureCollectionLoadIsSynchronous()
        val mockCollection = Mockito.mock(Collection::class.java, Mockito.RETURNS_DEEP_STUBS)
        val mockSched = Mockito.mock(Scheduler::class.java)
        whenever(mockCollection.sched).thenReturn(mockSched)
        whenever(mockSched.newCount()).thenReturn(0)
        val factory = CustomStudyDialogFactory({ mockCollection }, mockListener)
        val scenario = FragmentScenario.launch(CustomStudyDialog::class.java, args, androidx.appcompat.R.style.Theme_AppCompat, factory)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.onFragment { f: CustomStudyDialog ->
            val dialog = f.dialog as MaterialDialog?
            MatcherAssert.assertThat(dialog, IsNull.notNullValue())
            MatcherAssert.assertThat(dialog!!.items, Matchers.not(Matchers.hasItem(getResourceString(R.string.custom_study_increase_new_limit))))
        }
    }
}
