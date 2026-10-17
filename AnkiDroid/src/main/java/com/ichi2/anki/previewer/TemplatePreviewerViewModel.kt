// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteTypeProvider
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.asyncIO
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.clozeNumbersInNote
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.utils.ext.require
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.parcelize.Parcelize
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting

class TemplatePreviewerViewModel(
    savedStateHandle: SavedStateHandle,
) : CardViewerViewModel(savedStateHandle) {
    private val notetype: Deferred<NotetypeJson>
    private val fillEmpty: Boolean
    private val isCloze: Deferred<Boolean>

    /**
     * Identifies which of the card templates or cloze deletions it corresponds to
     *
     * @see CardOrdinal
     */
    @VisibleForTesting
    val ordFlow: MutableStateFlow<CardOrdinal>

    private val note: Deferred<Note>
    private val templateNames: Deferred<List<String>>
    private val clozeOrds: Deferred<List<CardOrdinal>?>
    override var currentCard: Deferred<Card>
    override val server = AnkiServer(this).also { it.start() }

    /**
     * Ordered list of cards with empty fronts
     */
    internal val cardsWithEmptyFronts: Deferred<List<Boolean>?>

    init {
        val arguments = savedStateHandle.require<TemplatePreviewerArguments>(TemplatePreviewerFragment.ARG_KEY)
        notetype =
            asyncIO {
                withCol { arguments.notetype(this) }
            }
        fillEmpty = arguments.fillEmpty
        isCloze = asyncIO { notetype.await().isCloze }
        ordFlow = MutableStateFlow(arguments.ord)

        note =
            asyncIO {
                withCol {
                    if (arguments.id != 0L) {
                        Note(this, arguments.id)
                    } else {
                        Note.fromNotetypeId(this@withCol, arguments.notetype(this).id)
                    }
                }.apply {
                    fields = arguments.fields.toMutableList()
                    tags = arguments.tags.toMutableList()
                }
            }
        currentCard =
            asyncIO {
                val note = note.await()
                val notetype = notetype.await()
                withCol {
                    note.ephemeralCard(
                        col = this,
                        ord = ordFlow.value,
                        customNoteType = notetype,
                        fillEmpty = fillEmpty,
                        deckId = arguments.deckId,
                    )
                }
            }

        clozeOrds =
            asyncIO {
                if (isCloze.await()) {
                    val note = note.await()
                    val clozeNumbers = withCol { clozeNumbersInNote(note) }
                    clozeNumbers.map { it - 1 }
                } else {
                    null
                }
            }

        templateNames =
            asyncIO {
                if (isCloze.await()) {
                    val note = note.await()
                    val clozeNumbers = withCol { clozeNumbersInNote(note) }
                    val tr = CollectionManager.TR
                    clozeNumbers.map { tr.cardTemplatesCard(it) }
                } else {
                    notetype.await().templatesNames
                }
            }

        cardsWithEmptyFronts =
            asyncIO {
                if (isCloze.await()) {
                    null
                } else {
                    val note = note.await()
                    val notetype = notetype.await()
                    List(templateNames.await().size) { ord ->
                        val questionText =
                            withCol {
                                note
                                    .ephemeralCard(
                                        col = this,
                                        ord = ord,
                                        customNoteType = notetype,
                                        fillEmpty = fillEmpty,
                                        deckId = arguments.deckId,
                                    ).renderOutput(this)
                            }.questionText
                        EMPTY_FRONT_LINK in questionText
                    }
                }
            }
    }

    /* *********************************************************************************************
     ************************ Public methods: meant to be used by the View **************************
     ********************************************************************************************* */

    override fun onPageFinished(isAfterRecreation: Boolean) {
        if (isAfterRecreation) {
            launchCatchingIO {
                // TODO: We should persist showingAnswer to SavedStateHandle
                if (showingAnswer.value) showAnswer() else showQuestion()
            }
            return
        }
        launchCatchingIO {
            ordFlow.collectLatest { ord ->
                currentCard =
                    asyncIO {
                        val note = note.await()
                        val notetype = notetype.await()
                        withCol {
                            note.ephemeralCard(
                                col = this,
                                ord = ord,
                                customNoteType = notetype,
                                fillEmpty = fillEmpty,
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
                showAnswer()
                loadAndPlaySounds(CardSide.ANSWER)
            }
        }
    }

    @CheckResult
    suspend fun getTemplateNames(): List<String> = templateNames.await()

    fun onTabSelected(position: Int) {
        launchCatchingIO {
            val ord =
                if (isCloze.await()) {
                    clozeOrds.await()!![position]
                } else {
                    position
                }
            ordFlow.emit(ord)
        }
    }

    @CheckResult
    suspend fun getCurrentTabIndex(): Int =
        if (isCloze.await()) {
            clozeOrds.await()!!.indexOf(ordFlow.value)
        } else {
            ordFlow.value
        }

    suspend fun getSafeClozeOrd(): CardOrdinal {
        val ords = clozeOrds.await() ?: return 0
        return if (ords.isEmpty()) 0 else ordFlow.value.coerceIn(0, ords.size - 1)
    }

    fun updateContent(
        fields: List<String>,
        tags: List<String>,
    ) {
        launchCatchingIO {
            // Update note fields and tags
            val note = note.await()
            note.fields = fields.toMutableList()
            note.tags = tags.toMutableList()

            currentCard =
                asyncIO {
                    val notetype = notetype.await()
                    withCol {
                        note.ephemeralCard(
                            col = this,
                            ord = ordFlow.value,
                            customNoteType = notetype,
                            fillEmpty = fillEmpty,
                        )
                    }
                }
            if (showingAnswer.value) {
                showAnswer()
                loadAndPlaySounds(CardSide.ANSWER)
            } else {
                showQuestion()
                loadAndPlaySounds(CardSide.QUESTION)
            }
        }
    }

    /* *********************************************************************************************
     *************************************** Internal methods ***************************************
     ********************************************************************************************* */

    private suspend fun loadAndPlaySounds(side: CardSide) {
        cardMediaPlayer.loadCardAvTags(currentCard.await())
        cardMediaPlayer.autoplayAllForSide(side)
    }

    // https://github.com/ankitects/anki/blob/df70564079f53e587dc44f015c503fdf6a70924f/qt/aqt/clayout.py#L579
    override suspend fun typeAnsFilter(text: String): String =
        if (showingAnswer.value) {
            val typeAnswer = TypeAnswer.getInstance(currentCard.await(), text)
            if (typeAnswer?.expectedAnswer?.isEmpty() == true) {
                typeAnswer.expectedAnswer = "sample"
            }
            typeAnswer?.answerFilter(typedAnswer = "example") ?: text
        } else {
            val repl = "<center><input id='typeans' type=text value='example' readonly='readonly'></center>"
            val warning = "<center><b>${CollectionManager.TR.cardTemplatesTypeBoxesWarning()}</b></center>"
            StringBuilder(text).replaceFirst(typeAnsRe, repl).replace(typeAnsRe, warning)
        }

    companion object {
        @Language("HTML")
        private const val EMPTY_FRONT_LINK = """<a href='https://docs.ankiweb.net/templates/errors.html#front-of-card-is-blank'>"""
    }
}

/**
 * @param id id of the note. Use 0 for non-created notes.
 * @param ord See [CardOrdinal]
 * @param fillEmpty if blank fields should be replaced with placeholder content
 */
@Parcelize
data class TemplatePreviewerArguments(
    private val notetypeProvider: NoteTypeProvider,
    val fields: List<String>,
    val tags: List<String>,
    val id: NoteId = 0,
    val ord: CardOrdinal = 0,
    val fillEmpty: Boolean = false,
    val deckId: DeckId = DEFAULT_DECK_ID,
) : Parcelable {
    fun notetype(col: Collection): NotetypeJson = notetypeProvider.getNoteType(col)

    companion object {
        /**
         * Returns `true` if [bundle] holds a [TemplatePreviewerArguments]
         * whose backing [NotetypeFile] is still readable. Use this before
         * constructing [TemplatePreviewerViewModel] to detect when the
         * temp file was cleaned up by the OS (e.g. after process death) so
         * the previewer can abort instead of throwing from the constructor.
         */
        fun isUsable(bundle: Bundle): Boolean =
            BundleCompat
                .getParcelable(bundle, TemplatePreviewerFragment.ARG_KEY, TemplatePreviewerArguments::class.java)
                ?.notetypeProvider
                ?.isUsable() ?: false
    }
}
