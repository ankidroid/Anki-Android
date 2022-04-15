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

package com.ichi2.async;

import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.RunInBackground;
import com.ichi2.anki.servicelayer.SearchService.SearchCardsResult;
import com.ichi2.libanki.SortOrder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class CollectionTaskSearchCardsTest extends AbstractCollectionTaskTest {

    @SuppressWarnings("unchecked")
    @Test
    @RunInBackground
    public void searchCardsNumberOfResultCount() {
        addNoteUsingBasicModel("Hello", "World");
        addNoteUsingBasicModel("One", "Two");

        int cardsToRender = 1;
        int numberOfCards = 2;

        CollectionTask.SearchCards task = new CollectionTask.SearchCards("", new SortOrder.NoOrdering(), cardsToRender, 0, 0);
        TaskListener<List<CardBrowser.CardCache>, SearchCardsResult> listener = mock(TaskListener.class);

        waitForTask(task, listener);

        verify(listener, times(1)).onPreExecute();

        ArgumentCaptor<List<CardBrowser.CardCache>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(listener, times(1)).onProgressUpdate(argumentCaptor.capture());
        assertThat("OnProgress sends the provided number of cards to render", argumentCaptor.getValue().size(), is(cardsToRender));

        ArgumentCaptor<SearchCardsResult> argumentCaptor2 = ArgumentCaptor.forClass(SearchCardsResult.class);
        verify(listener, times(1)).onPostExecute(argumentCaptor2.capture());
        assertThat("All cards should be provided on Post Execute", argumentCaptor2.getValue().size(), is(numberOfCards));

    }
}