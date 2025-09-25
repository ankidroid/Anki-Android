package com.ichi2.anki

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.ui.compose.StudyOptionsData
import com.ichi2.anki.ui.compose.StudyOptionsScreen
import com.ichi2.anki.utils.ext.showDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StudyOptionsComposeActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var studyOptionsData by remember { mutableStateOf<StudyOptionsData?>(null) }

            LaunchedEffect(Unit) {
                studyOptionsData =
                    withContext(Dispatchers.IO) {
                        CollectionManager.withCol {
                            val deck = decks.current()
                            val counts = sched.counts()
                            var buriedNew = 0
                            var buriedLearning = 0
                            var buriedReview = 0
                            val tree = sched.deckDueTree(deck.id)
                            if (tree != null) {
                                buriedNew = tree.newCount - counts.new
                                buriedLearning = tree.learnCount - counts.lrn
                                buriedReview = tree.reviewCount - counts.rev
                            }
                            StudyOptionsData(
                                deckId = deck.id,
                                deckName = deck.getString("name"),
                                deckDescription = deck.description,
                                newCount = counts.new,
                                lrnCount = counts.lrn,
                                revCount = counts.rev,
                                buriedNew = buriedNew,
                                buriedLrn = buriedLearning,
                                buriedRev = buriedReview,
                                totalNewCards = sched.totalNewForCurrentDeck(),
                                totalCards = decks.cardCount(deck.id, includeSubdecks = true),
                                isFiltered = deck.isFiltered,
                                haveBuried = sched.haveBuried(),
                            )
                        }
                    }
            }

            StudyOptionsScreen(
                studyOptionsData = studyOptionsData,
                onStartStudy = {
                    startActivity(Reviewer.getIntent(this))
                },
                onCustomStudy = { deckId ->
                    showDialogFragment(CustomStudyDialog.createInstance(deckId))
                },
            )
        }
    }
}
