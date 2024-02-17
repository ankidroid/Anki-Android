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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.utils.ext.ifNullOrEmpty
import com.ichi2.libanki.Note
import com.ichi2.libanki.NotetypeJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize
import org.intellij.lang.annotations.Language

class TemplatePreviewerViewModel(arguments: TemplatePreviewerArguments) : CardViewerViewModel() {
    private val notetype = arguments.notetype
    private val fillEmpty = arguments.fillEmpty

    /**
     * identifies which of the card templates or cloze deletions it corresponds to
     * * for card templates, values are from 0 to the number of templates minus 1
     * * for cloze deletions, values are from 0 to max cloze index minus 1
     */
    private val ordFlow = MutableStateFlow(arguments.ord)

    private lateinit var note: Note
    private lateinit var templateNames: List<String>
    private var initJob: Job? = null

    init {
        initJob = launchCatchingIO {
            note = withCol {
                if (arguments.id != 0L) {
                    Note(this, arguments.id)
                } else {
                    Note.fromNotetypeId(this, arguments.notetype.id)
                }
            }.apply {
                fields = arguments.fields
                tags = arguments.tags
            }

            templateNames = if (notetype.isCloze) {
                val tr = CollectionManager.TR
                withCol { clozeNumbersInNote(note) }.map { tr.cardTemplatesCloze(it) }
            } else {
                notetype.templatesNames
            }
        }.also {
            it.invokeOnCompletion {
                initJob = null
            }
        }
    }

    /* *********************************************************************************************
    ************************ Public methods: meant to be used by the View **************************
    ********************************************************************************************* */

    override fun onPageFinished(isAfterRecreation: Boolean) {
        if (isAfterRecreation) {
            launchCatchingIO {
                if (showingAnswer.value) showAnswer() else showQuestion()
            }
            return
        }
        launchCatchingIO {
            initJob?.join()
            ordFlow.collectLatest {
                currentCard = withCol {
                    note.ephemeralCard(
                        col = this,
                        ord = ordFlow.value,
                        customNoteType = notetype,
                        fillEmpty = fillEmpty
                    )
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
                showAnswer()
                loadAndPlaySounds(CardSide.ANSWER)
            }
        }
    }

    suspend fun getTemplateNames(): List<String> {
        initJob?.join()
        return templateNames
    }

    fun onTabSelected(ord: Int) {
        launchCatchingIO { ordFlow.emit(ord) }
    }

    fun getCurrentTabIndex(): Int = ordFlow.value

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    private suspend fun loadAndPlaySounds(side: CardSide) {
        soundPlayer.loadCardSounds(currentCard)
        soundPlayer.playAllSoundsForSide(side)
    }

    // https://github.com/ankitects/anki/blob/df70564079f53e587dc44f015c503fdf6a70924f/qt/aqt/clayout.py#L579
    override suspend fun typeAnsFilter(text: String): String {
        val typeAnswerField = getTypeAnswerField(currentCard, text)
        val expectedAnswer = typeAnswerField?.let {
            getExpectedTypeInAnswer(currentCard, typeAnswerField)
        }.ifNullOrEmpty { "example" }

        val repl = if (showingAnswer.value) {
            withCol { compareAnswer(expectedAnswer, "sample") }
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
        fun factory(arguments: TemplatePreviewerArguments): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    TemplatePreviewerViewModel(arguments)
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
