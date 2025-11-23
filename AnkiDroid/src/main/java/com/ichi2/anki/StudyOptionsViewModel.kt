package com.ichi2.anki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class StudyOptionsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StudyOptionsState())
    val uiState: StateFlow<StudyOptionsState> = _uiState.asStateFlow()

    fun refreshData() {
        viewModelScope.launch {
            // Set loading to true immediately
            _uiState.value = _uiState.value.copy(isLoading = true)

            if (!CollectionManager.isOpenUnsafe()) {
                // If collection is closed, just stop loading
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            try {
                val data = withCol { fetchStudyOptionsData() }
                _uiState.value = _uiState.value.copy(data = data, isLoading = false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch study options data")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateData(newData: DeckStudyData) {
        _uiState.value = _uiState.value.copy(data = newData)
    }
}

/**
 * See https://github.com/ankitects/anki/blob/b05c9d15986ab4e33daa2a47a947efb066bb69b6/qt/aqt/overview.py#L226-L272
 */
fun Collection.fetchStudyOptionsData(): DeckStudyData {
    val deckId = decks.current().id
    val counts = sched.counts()
    var buriedNew = 0
    var buriedLearning = 0
    var buriedReview = 0
    val tree = sched.deckDueTree(deckId)
    if (tree != null) {
        buriedNew = tree.newCount - counts.new
        buriedLearning = tree.learnCount - counts.lrn
        buriedReview = tree.reviewCount - counts.rev
    }
    return DeckStudyData(
        newCardsToday = counts.new,
        lrnCardsToday = counts.lrn,
        revCardsToday = counts.rev,
        buriedNew = buriedNew,
        buriedLearning = buriedLearning,
        buriedReview = buriedReview,
        totalNewCards = sched.totalNewForCurrentDeck(),
        numberOfCardsInDeck = decks.cardCount(deckId, includeSubdecks = true),
    )
}

data class StudyOptionsState(
    val data: DeckStudyData? = null,
    val isLoading: Boolean = false,
)

data class DeckStudyData(
    /**
     * The number of new card to see today in a deck, including subdecks.
     */
    val newCardsToday: Int,
    /**
     * The number of (repetition of) card in learning to see today in a deck, including subdecks. The exact way cards with multiple steps are counted depends on the scheduler
     */
    val lrnCardsToday: Int,
    /**
     * The number of review card to see today in a deck, including subdecks.
     */
    val revCardsToday: Int,
    val buriedNew: Int,
    val buriedLearning: Int,
    val buriedReview: Int,
    val totalNewCards: Int,
    /**
     * Number of cards in this decks and its subdecks.
     */
    val numberOfCardsInDeck: Int,
)
