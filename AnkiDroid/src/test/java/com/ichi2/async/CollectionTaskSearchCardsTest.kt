/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.async

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser.CardCache
import com.ichi2.anki.RunInBackground
import com.ichi2.anki.servicelayer.SearchService.SearchCardsResult
import com.ichi2.async.CollectionTask.SearchCards
import com.ichi2.libanki.SortOrder.NoOrdering
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.argumentCaptor

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("is -> equalTo")
@KotlinCleanup("scope functions for argument matchers")
class CollectionTaskSearchCardsTest : AbstractCollectionTaskTest() {
    @Test
    @RunInBackground
    fun searchCardsNumberOfResultCount() {
        addNoteUsingBasicModel("Hello", "World")
        addNoteUsingBasicModel("One", "Two")

        val cardsToRender = 1
        val numberOfCards = 2

        val task = SearchCards("", NoOrdering(), cardsToRender, 0, 0)
        @Suppress("UNCHECKED_CAST")
        val listener: TaskListener<List<CardCache>, SearchCardsResult> = mock(TaskListener::class.java) as TaskListener<List<CardCache>, SearchCardsResult>

        waitForTask(task, listener)

        verify(listener, times(1)).onPreExecute()
        val argumentCaptor = argumentCaptor<List<CardCache>>()
        verify(listener, times(1)).onProgressUpdate(argumentCaptor.capture())
        assertThat("OnProgress sends the provided number of cards to render", argumentCaptor.firstValue.size, `is`(cardsToRender))

        val argumentCaptor2 = argumentCaptor<SearchCardsResult>()
        verify(listener, times(1)).onPostExecute(argumentCaptor2.capture())
        assertThat("All cards should be provided on Post Execute", argumentCaptor2.firstValue.size(), `is`(numberOfCards))
    }
}
