// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.utils.ext.ifNotZero
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.createTransientDirectory
import timber.log.Timber
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

abstract class CardBrowserViewModelTestBase : JvmTest() {
    override fun setUp() {
        super.setUp()
        Prefs.putString(com.ichi2.anki.R.string.pref_browser_no_sorting, null)
        preventDayRolloverAlarmFromUnburyingCards()
    }

    protected fun runViewModelNotesTest(
        notes: Int = 0,
        initMode: InitMode = InitMode.AUTOMATIC,
        options: CardBrowserLaunchOptions? = null,
        testBody: suspend CardBrowserViewModel.() -> Unit,
    ) = runTest {
        CardsOrNotes.NOTES.saveToCollection(col)
        repeat(notes) {
            // ensure 1 note = 2 cards
            addBasicAndReversedNote()
        }
        val viewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                idsFileDirectory = createTransientDirectory(),
                options = options,
                initialDeckId = null,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = false,
                manualInit = initMode == InitMode.MANUAL || initMode == InitMode.AUTOMATIC,
                savedStateHandle = SavedStateHandle(),
            )
        // makes ignoreValuesFromViewModelLaunch work under test
        if (initMode == InitMode.AUTOMATIC) {
            viewModel.manualInit()
        }
        testBody(viewModel)
    }

    protected fun runViewModelTest(
        notes: Int = 0,
        initMode: InitMode = InitMode.AUTOMATIC,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        options: CardBrowserLaunchOptions? = null,
        isFragmented: Boolean = false,
        cacheDir: File = createTransientDirectory(),
        testBody: suspend CardBrowserViewModel.() -> Unit,
    ) = runTest {
        repeat(notes) {
            addBasicNote()
        }
        notes.ifNotZero { count -> Timber.d("added %d notes", count) }
        val viewModel =
            CardBrowserViewModel(
                lastDeckIdRepository = SharedPreferencesLastDeckIdRepository(),
                idsFileDirectory = cacheDir,
                options = options,
                initialDeckId = null,
                preferences = AnkiDroidApp.sharedPreferencesProvider,
                isFragmented = isFragmented,
                manualInit = initMode == InitMode.MANUAL || initMode == InitMode.AUTOMATIC,
                savedStateHandle = savedStateHandle,
            )
        // makes ignoreValuesFromViewModelLaunch work under test
        if (initMode == InitMode.AUTOMATIC) {
            viewModel.manualInit()
        }
        testBody(viewModel)
        Timber.d("end runViewModelTest")
    }

    protected suspend fun viewModel(
        lastDeckId: DeckId? = null,
        initialDeckId: DeckId? = null,
        intent: CardBrowserLaunchOptions? = null,
        mode: CardsOrNotes = CardsOrNotes.CARDS,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        lastDeckIdRepository: LastDeckIdRepository =
            object : LastDeckIdRepository {
                override var lastDeckId: DeckId? = lastDeckId
            },
    ): CardBrowserViewModel {
        // default is CARDS, do nothing in this case
        if (mode == CardsOrNotes.NOTES) {
            CollectionManager.withCol { mode.saveToCollection(this@withCol) }
        }

        val cache = File(createTempDirectory().pathString)
        return CardBrowserViewModel(
            lastDeckIdRepository = lastDeckIdRepository,
            idsFileDirectory = cache,
            options = intent,
            initialDeckId = initialDeckId,
            isFragmented = false,
            preferences = AnkiDroidApp.sharedPreferencesProvider,
            savedStateHandle = savedStateHandle,
        ).apply {
            invokeInitialSearch()
        }
    }

    enum class InitMode {
        /** init { } runs as normal */
        NO_DELAY,

        /** init { } is delayed, via a call to [CardBrowserViewModel.manualInit] is called */
        AUTOMATIC,

        /** init is not run, and a manual invocation of [CardBrowserViewModel.manualInit] is necessary */
        MANUAL,
    }
}
