/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;

import com.ichi2.libanki.Card;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class PreviewerTest extends RobolectricForegroundTest {

    @Test
    public void editingNoteDoesNotChangePreviewedCardId() {
        // #7801
        addNoteUsingBasicModel("Hello", "World");

        Card cardToPreview = addNoteUsingBasicModel("Hello", "World").firstCard();
        setDeck("Deck", cardToPreview);

        Previewer previewer = getPreviewerPreviewing(cardToPreview);

        assertThat("Initially should be previewing selected card", previewer.getCurrentCardId(), is(cardToPreview.getId()));

        previewer.onActivityResult(AbstractFlashcardViewer.EDIT_CURRENT_CARD, Activity.RESULT_OK, null);

        advanceRobolectricLooperWithSleep();

        assertThat("Should be previewing selected card after edit", previewer.getCurrentCardId(), is(cardToPreview.getId()));
    }

    @Test
    public void editingNoteChangesContent() {
        // #7801
        addNoteUsingBasicModel("Hello", "World");

        Card cardToPreview = addNoteUsingBasicModel("Hello", "World").firstCard();
        setDeck("Deck", cardToPreview);

        Previewer previewer = getPreviewerPreviewing(cardToPreview);

        assertThat("Initial content assumption", previewer.getCardContent(), not(containsString("Hi")));

        cardToPreview.note().setField(0, "Hi");

        previewer.onActivityResult(AbstractFlashcardViewer.EDIT_CURRENT_CARD, Activity.RESULT_OK, null);

        advanceRobolectricLooperWithSleep();

        assertThat("Card content should be updated after editing", previewer.getCardContent(), containsString("Hi"));
    }

    @Test
    public void previewerShouldNotAccessScheduler() {
        Card cardToPreview = addNoteUsingBasicModel("Hello", "World").firstCard();
        Previewer previewer = getPreviewerPreviewing(cardToPreview);

        assertThat("Previewer is not a reviewer", previewer.canAccessScheduler(), is(false));
    }


    @SuppressWarnings("SameParameterValue")
    protected void setDeck(String name, Card card) {
        long did = addDeck(name);
        card.setDid(did);
        card.flush();
    }


    protected Previewer getPreviewerPreviewing(Card usableCard) {
        Intent previewIntent = Previewer.getPreviewIntent(getTargetContext(), 0, new long[] { usableCard.getId() });
        Previewer previewer = super.startActivityNormallyOpenCollectionWithIntent(Previewer.class, previewIntent);
        AbstractFlashcardViewer.setEditorCard(usableCard);
        return previewer;
    }
}
