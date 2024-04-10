/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.SoundPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.utils.ext.ifNullOrEmpty
import com.ichi2.libanki.Card
import com.ichi2.libanki.Note
import com.ichi2.libanki.NotetypeJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting

class TemplatePreviewerViewModel(
    arguments: TemplatePreviewerArguments,
    soundPlayer: SoundPlayer
) : CardViewerViewModel(soundPlayer) {
    private val notetype = arguments.notetype
    private val fillEmpty = arguments.fillEmpty
    private val isCloze = notetype.isCloze

    /**
     * identifies which of the card templates or cloze deletions it corresponds to
     * * for card templates, values are from 0 to the number of templates minus 1
     * * for cloze deletions, values are from 0 to max cloze index minus 1
     */
    @VisibleForTesting
    val ordFlow = MutableStateFlow(arguments.ord)

    private val note: Deferred<Note>
    private val templateNames: Deferred<List<String>>
    private val clozeOrds: Deferred<List<Int>>?
    override lateinit var currentCard: Deferred<Card>

    init {
        note = asyncIO {
            withCol {
                if (arguments.id != 0L) {
                    Note(this, arguments.id)
                } else {
                    Note.fromNotetypeId(arguments.notetype.id)
                }
            }.apply {
                fields = arguments.fields
                tags = arguments.tags
            }
        }

        if (isCloze) {
            val clozeNumbers = asyncIO {
                val note = note.await()
                withCol { clozeNumbersInNote(note) }
            }
            clozeOrds = asyncIO {
                clozeNumbers.await().map { it - 1 }
            }
            templateNames = asyncIO {
                val tr = CollectionManager.TR
                clozeNumbers.await().map { tr.cardTemplatesCard(it) }
            }
        } else {
            clozeOrds = null
            templateNames = CompletableDeferred(notetype.templatesNames)
        }
    }

    /* *********************************************************************************************
    ************************ Public methods: meant to be used by the View **************************
    ********************************************************************************************* */

    override fun onPageFinished(isAfterRecreation: Boolean) {
        if (isAfterRecreation) {
            launchCatchingIO {
                if (showingAnswer.value) showAnswerInternal() else showQuestion()
            }
            return
        }
        launchCatchingIO {
            ordFlow.collectLatest { ord ->
                currentCard = asyncIO {
                    val note = note.await()
                    withCol {
                        note.ephemeralCard(
                            col = this,
                            ord = ord,
                            customNoteType = notetype,
                            fillEmpty = fillEmpty
                        )
                    }
                }
                showQuestion()
                loadAndPlaySounds(CardSide.QUESTION)
            }
        }
    }

    fun toggleShowAnswer() {
        launchCatchingIO {
            if (showingAnswer.value) {
                showQuestion()
                loadAndPlaySounds(CardSide.QUESTION)
            } else {
                showAnswerInternal()
                loadAndPlaySounds(CardSide.ANSWER)
            }
        }
    }

    @CheckResult
    suspend fun getTemplateNames(): List<String> {
        return templateNames.await()
    }

    fun onTabSelected(position: Int) {
        launchCatchingIO {
            val ord = if (isCloze) {
                clozeOrds!!.await()[position]
            } else {
                position
            }
            ordFlow.emit(ord)
        }
    }

    @CheckResult
    suspend fun getCurrentTabIndex(): Int {
        return if (isCloze) {
            clozeOrds!!.await().indexOf(ordFlow.value)
        } else {
            ordFlow.value
        }
    }

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    private suspend fun loadAndPlaySounds(side: CardSide) {
        soundPlayer.loadCardSounds(currentCard.await())
        soundPlayer.playAllSoundsForSide(side)
    }

    // https://github.com/ankitects/anki/blob/df70564079f53e587dc44f015c503fdf6a70924f/qt/aqt/clayout.py#L579
    override suspend fun typeAnsFilter(text: String): String {
        val typeAnswerField = getTypeAnswerField(currentCard.await(), text)
        val expectedAnswer = typeAnswerField?.let {
            getExpectedTypeInAnswer(currentCard.await(), typeAnswerField)
        }.ifNullOrEmpty { "sample" }

        val repl = if (showingAnswer.value) {
            withCol { compareAnswer(expectedAnswer, "example") }
        } else {
            "<center><input id='typeans' type=text value='example' readonly='readonly'></center>"
        }
        // Anki doesn't set the font size of the type answer field in the template previewer,
        // but it does in the reviewer. To get a more accurate preview of what people are going
        // to study, the font size is being set here.
        val out = if (typeAnswerField != null) {
            val fontSize = getFontSize(typeAnswerField)

            @Language("HTML")
            val replWithFontSize = """<div style="font-size: ${fontSize}px">$repl</div>"""
            typeAnsRe.replaceFirst(text, replWithFontSize)
        } else {
            typeAnsRe.replaceFirst(text, repl)
        }

        val warning = "<center><b>${CollectionManager.TR.cardTemplatesTypeBoxesWarning()}</b></center>"
        return typeAnsRe.replace(out, warning)
    }

    companion object {
        fun factory(arguments: TemplatePreviewerArguments, soundPlayer: SoundPlayer): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    TemplatePreviewerViewModel(arguments, soundPlayer)
                }
            }
        }
    }
}

/**
 * @param id id of the note. Use 0 for non-created notes.
 *
 * @param ord identifies which of the card templates or cloze deletions it corresponds to
 * * for card templates, values are from 0 to the number of templates minus 1
 * * for cloze deletions, values are from 0 to max cloze index minus 1
 *
 * @param fillEmpty if blank fields should be replaced with placeholder content
 */
@Parcelize
data class TemplatePreviewerArguments(
    private val notetypeFile: NotetypeFile,
    val fields: MutableList<String>,
    val tags: MutableList<String>,
    val id: Long = 0,
    val ord: Int = 0,
    val fillEmpty: Boolean = false
) : Parcelable {
    val notetype: NotetypeJson get() = notetypeFile.getNotetype()
}
