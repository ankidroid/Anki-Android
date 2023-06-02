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
package com.ichi2.anki

import android.widget.SeekBar
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.Card
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PreviewerTest : RobolectricTest() {

    @Test
    fun editingNoteDoesNotChangePreviewedCardId() = runTest {
        // #7801
        addNoteUsingBasicModel("Hello", "World")

        val cardToPreview = addNoteUsingBasicModel("Hello", "World").firstCard()
        setDeck("Deck", cardToPreview)

        val previewer = getPreviewerPreviewing(cardToPreview)

        assertThat("Initially should be previewing selected card", previewer.currentCardId, equalTo(cardToPreview.id))

        previewer.saveEditedCard()

        assertThat("Should be previewing selected card after edit", previewer.currentCardId, equalTo(cardToPreview.id))
    }

    @Test
    fun editingNoteChangesContent() = runTest {
        // #7801
        addNoteUsingBasicModel("Hello", "World")

        val cardToPreview = addNoteUsingBasicModel("Hello", "World").firstCard()
        setDeck("Deck", cardToPreview)

        val previewer = getPreviewerPreviewing(cardToPreview)

        assertThat("Initial content assumption", previewer.cardContent, not(containsString("Hi")))

        cardToPreview.note(col).setField(0, "Hi")

        previewer.saveEditedCard()

        assertThat("Card content should be updated after editing", previewer.cardContent, containsString("Hi"))
    }

    @Test
    fun previewerShouldNotAccessScheduler() {
        val cardToPreview = addNoteUsingBasicModel("Hello", "World").firstCard()
        val previewer = getPreviewerPreviewing(cardToPreview)

        assertThat("Previewer is not a reviewer", previewer.canAccessScheduler(), equalTo(false))
    }

    @Test
    @Config(qualifiers = "en")
    fun seekbarOnStart() {
        val previewer = seekBarHelper()
        val s = previewer.findViewById<SeekBar>(R.id.preview_progress_seek_bar)
        val t = previewer.findViewById<TextView>(R.id.preview_progress_text)
        assertThat("Progress is 0 at the beginning.", s.progress, equalTo(0))
        assertThat("Progress text at the beginning.", t.text, equalTo(previewer.getString(R.string.preview_progress_bar_text, 1, 6)))
    }

    @Test
    @Config(qualifiers = "en")
    fun seekBarMax() {
        val previewer = seekBarHelper()
        val s = previewer.findViewById<SeekBar>(R.id.preview_progress_seek_bar)
        val t = previewer.findViewById<TextView>(R.id.preview_progress_text)
        assertThat("Max value of seekbar", s.max, equalTo(5))
        assertThat("Progress text at the beginning.", t.text, equalTo(previewer.getString(R.string.preview_progress_bar_text, 1, 6)))
    }

    @Test
    @Config(qualifiers = "en")
    fun seekBarOnNavigationForward() {
        val previewer = seekBarHelper()
        val s = previewer.findViewById<SeekBar>(R.id.preview_progress_seek_bar)
        val t = previewer.findViewById<TextView>(R.id.preview_progress_text)
        val y = s.progress
        previewer.changePreviewedCard(true)
        previewer.changePreviewedCard(true)
        assertThat("Seekbar value when you preview two cards", y, equalTo(s.progress - 2))
        assertThat("Progress text at the beginning.", t.text, equalTo(previewer.getString(R.string.preview_progress_bar_text, 3, 6)))
    }

    @Test
    @Config(qualifiers = "en")
    fun seekBarOnNavigationBackward() {
        val previewer = seekBarHelper()
        val s = previewer.findViewById<SeekBar>(R.id.preview_progress_seek_bar)
        val t = previewer.findViewById<TextView>(R.id.preview_progress_text)
        previewer.changePreviewedCard(true)
        previewer.changePreviewedCard(true)
        val y = s.progress
        previewer.changePreviewedCard(false)
        assertThat("Progress text at the beginning.", t.text, equalTo(previewer.getString(R.string.preview_progress_bar_text, 2, 6)))
        previewer.changePreviewedCard(false)
        assertThat("Seekbar value when you go back two cards", s.progress, equalTo(y - 2))
        assertThat("Progress text at the beginning.", t.text, equalTo(previewer.getString(R.string.preview_progress_bar_text, 1, 6)))
    }

    private fun seekBarHelper(): Previewer {
        val front = arrayOf("1", "2", "3", "4", "5", "6")
        val back = arrayOf("7", "8", "9", "10", "11", "12")
        val c = arrayOfNulls<Card>(front.size)
        val arrayList = LongArray(front.size)
        for (i in front.indices) {
            val cardToPreview = addNoteUsingBasicModel(front[i], back[i]).firstCard()
            setDeck("Deck", cardToPreview)
            val h = cardToPreview.id
            arrayList[i] = h
            c[i] = cardToPreview
        }
        return getPreviewerPreviewingList(arrayList, c)
    }

    private fun setDeck(name: String?, card: Card) {
        val did = addDeck(name)
        card.did = did
        card.flush(col)
    }

    private fun getPreviewerPreviewingList(arr: LongArray, c: Array<Card?>): Previewer {
        val previewIntent = Previewer.getPreviewIntent(targetContext, 0, arr)
        val previewer = super.startActivityNormallyOpenCollectionWithIntent(Previewer::class.java, previewIntent)
        for (i in arr.indices) {
            AbstractFlashcardViewer.editorCard = c[i]
        }
        return previewer
    }

    private fun getPreviewerPreviewing(usableCard: Card): Previewer {
        val previewIntent = Previewer.getPreviewIntent(targetContext, 0, longArrayOf(usableCard.id))
        val previewer = super.startActivityNormallyOpenCollectionWithIntent(Previewer::class.java, previewIntent)
        AbstractFlashcardViewer.editorCard = usableCard
        return previewer
    }
}
