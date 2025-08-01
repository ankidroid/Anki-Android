// noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.children
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.testutils.ext.addBasicNoteWithOp
import com.ichi2.testutils.ext.menu
import com.ichi2.testutils.grantWritePermissions
import com.ichi2.testutils.revokeWritePermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import timber.log.Timber
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@KotlinCleanup("SPMockBuilder")
@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var mQualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<String> = listOf("normal", "xlarge")
    }

    @Before
    fun before() {
        RuntimeEnvironment.setQualifiers(mQualifiers)
        getPreferences().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
        }
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom201to292() {
        val newVersion = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion.toLong()))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPicker.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.1")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPicker.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion =
                    deckPicker.getPreviousVersion(preferences, newVersion.toLong())
                assertEquals(0, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPicker.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom202to292() {
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPicker.UPGRADE_VERSION_KEY, 20900203))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getString(DeckPicker.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.2")
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPicker.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.getPreviousVersion(preferences, newVersion)
                assertEquals(40, previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPicker.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    @SuppressLint("UseKtx")
    fun getPreviousVersionUpgradeFrom281to291() {
        val prevVersion = 20800301 // 2.8.1
        val newVersion: Long = 20900301 // 2.9.1
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        whenever(preferences.getInt(DeckPicker.UPGRADE_VERSION_KEY, 20900203))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        whenever(editor.remove(DeckPicker.UPGRADE_VERSION_KEY)).thenReturn(updated)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion.toLong(), previousVersion)
            }
        }
        verify(editor, times(1)).remove(DeckPicker.UPGRADE_VERSION_KEY)
        verify(updated, times(1)).apply()
    }

    @Test
    fun getPreviousVersionUpgradeFrom291to292() {
        val prevVersion: Long = 20900301 // 2.9.1
        val newVersion: Long = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        whenever(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        whenever(preferences.edit()).thenReturn(editor)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                val previousVersion = deckPicker.getPreviousVersion(preferences, newVersion)
                assertEquals(prevVersion, previousVersion)
            }
        }
        verify(editor, never()).remove(DeckPicker.UPGRADE_VERSION_KEY)
    }

    @Test
    fun limitAppliedAfterReview() {
        val sched = col.sched
        val dconf = col.decks.getConfig(1)
        assertNotNull(dconf)
        dconf.new.perDay = 10
        col.decks.save(dconf)
        for (i in 0..10) {
            addBasicNote("Which card is this ?", i.toString())
        }
        // This set a card as current card
        sched.card
        ensureCollectionLoadIsSynchronous()
        val deckPicker =
            super.startActivityNormallyOpenCollectionWithIntent(
                DeckPicker::class.java,
                Intent(),
            )
        assertEquals(
            10,
            deckPicker.dueTree!!
                .children[0]
                .newCount
                .toLong(),
        )
    }

    @Test
    fun confirmDeckDeletionDeletesEmptyDeck() =
        runTest {
            val did = addDeck("Hello World")
            assertThat("Deck was added", col.decks.count(), equalTo(2))
            val deckPicker =
                startActivityNormallyOpenCollectionWithIntent(
                    DeckPicker::class.java,
                    Intent(),
                )
            deckPicker.viewModel.deleteDeck(did).join()
            assertThat("deck was deleted", col.decks.count(), equalTo(1))
        }

    @Test
    fun databaseLockedTest() {
        // don't call .onCreate
        val deckPicker = Robolectric.buildActivity(DeckPickerEx::class.java, Intent()).get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DatabaseLocked)
        assertThat(
            deckPicker.databaseErrorDialog,
            equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
        )
    }

    @Test
    fun databaseLockedWithPermissionIntegrationTest() {
        AnkiDroidApp.sentExceptionReportHack = false
        try {
            BackendEmulatingOpenConflict.enable()
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "A specific dialog for a conflict should be shown",
                d.databaseErrorDialog,
                equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
            )
            assertThat(
                "No exception reports should be thrown",
                AnkiDroidApp.sentExceptionReportHack,
                equalTo(false),
            )
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    @Ignore("Flaky. Try to unflake now we're using coroutines")
    fun databaseLockedNoPermissionIntegrationTest() {
        // no permissions -> grant permissions -> db locked
        try {
            InitialActivityWithConflictTest.setupForDefault()
            BackendEmulatingOpenConflict.enable()
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )

            // grant permissions
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            d.onStoragePermissionGranted()
            assertThat(
                "A specific dialog for a conflict should be shown",
                d.databaseErrorDialog,
                equalTo(DatabaseErrorDialogType.DIALOG_DB_LOCKED),
            )
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun deckPickerOpensWithHelpMakeAnkiDroidBetterDialog() {
        // Refactor: It would be much better to use a spy - see if we can get this into Robolectric
        try {
            grantWritePermissions()
            BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
            // We don't show it if the user is new.
            targetContext
                .sharedPrefs()
                .edit { putString("lastVersion", "0.1") }
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "Analytics opt-in should be displayed",
                d.displayedAnalyticsOptIn,
                equalTo(true),
            )
        } finally {
            revokeWritePermissions()
            BackupManagerTestUtilities.reset()
        }
    }

    @Test
    fun doNotShowOptionsMenuWhenCollectionInaccessible() =
        runTest {
            try {
                enableNullCollection()
                val d =
                    super.startActivityNormallyOpenCollectionWithIntent(
                        DeckPickerEx::class.java,
                        Intent(),
                    )
                d.updateMenuState()
                assertThat(
                    "Options menu not displayed when collection is inaccessible",
                    d.optionsMenuState,
                    equalTo(null),
                )
            } finally {
                disableNullCollection()
            }
        }

    @Test
    fun showOptionsMenuWhenCollectionAccessible() =
        runTest {
            try {
                grantWritePermissions()
                val d =
                    super.startActivityNormallyOpenCollectionWithIntent(
                        DeckPickerEx::class.java,
                        Intent(),
                    )
                d.updateMenuState()
                assertThat(
                    "Options menu displayed when collection is accessible",
                    d.optionsMenuState,
                    notNullValue(),
                )
            } finally {
                revokeWritePermissions()
            }
        }

    @Test
    fun onResumeLoadCollectionFailureWithInaccessibleCollection() {
        try {
            revokeWritePermissions()
            enableNullCollection()
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )

            // Neither collection, not its models will be initialized without storage permission

            // assert: Lazy Collection initialization CollectionTask.LoadCollectionComplete fails
            assertFailsWith<Exception> { d.getColUnsafe }
        } finally {
            disableNullCollection()
        }
    }

    @Test
    fun onResumeLoadCollectionSuccessWithAccessibleCollection() {
        try {
            grantWritePermissions()
            val d =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            assertThat(
                "Collection initialization ensured by CollectionTask.LoadCollectionComplete",
                d.getColUnsafe,
                notNullValue(),
            )
            assertThat(
                "Collection Models Loaded",
                d.getColUnsafe.notetypes,
                notNullValue(),
            )
        } finally {
            revokeWritePermissions()
        }
    }

    @Test
    fun `ContextMenu starts expected dialogs when specific options are selected`() =
        runTest {
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
                val didA = addDeck("Deck 1")

                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.RENAME_DECK, didA)
                assertDialogTitleEquals("Rename deck")
                dismissAllDialogFragments()

                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.CREATE_SUBDECK, didA)
                assertDialogTitleEquals("Create subdeck")
                dismissAllDialogFragments()

                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.CUSTOM_STUDY, didA)
                assertDialogTitleEquals("Custom study")
                dismissAllDialogFragments()

//            TODO test code enters in a recursion in BasicItemSelectedListener inside ExportDialog
//            supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.EXPORT_DECK, didA)
//            assertAlertDialogTitleEquals("Export")
//            dismissAllDialogFragments()
            }
        }

    /** Simulates a selection in the context menu by setting the specific result in FragmentManager */
    private fun FragmentManager.selectContextMenuOption(
        option: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) {
        val arguments =
            Bundle().apply {
                putLong(DeckPickerContextMenu.CONTEXT_MENU_DECK_ID, deckId)
                putSerializable(DeckPickerContextMenu.CONTEXT_MENU_DECK_OPTION, option)
            }
        setFragmentResult(DeckPickerContextMenu.REQUEST_KEY_CONTEXT_MENU, arguments)
    }

    private fun assertDialogTitleEquals(expectedTitle: String) {
        val actualTitle = (ShadowDialog.getLatestDialog() as AlertDialog).title
        Timber.d("titles = \"$actualTitle\", \"$expectedTitle\"")
        assertEquals(expectedTitle, actualTitle)
    }

    @Test
    fun `ContextMenu starts expected activities when specific options are selected`() =
        runTest {
            suspend fun DeckPicker.selectContextMenuOptionForActivity(
                option: DeckPickerContextMenuOption,
                deckId: DeckId,
            ): Intent {
                var result: Destination? = null
                viewModel.flowOfDestination.test(1.seconds) {
                    supportFragmentManager.selectContextMenuOption(option, deckId)
                    result = awaitItem()
                }
                return result!!.toIntent(this)
            }

            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
                val didA = addDeck("Deck 1")
                val didDynamicA = addDynamicDeck("Deck Dynamic 1")

                val noteEditor = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.ADD_CARD, didA)
                assertEquals("com.ichi2.anki.NoteEditorActivity", noteEditor.component!!.className)
                onBackPressedDispatcher.onBackPressed()

                val browser = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.BROWSE_CARDS, didA)
                assertEquals("com.ichi2.anki.CardBrowser", browser.component!!.className)
                onBackPressedDispatcher.onBackPressed()

                // select deck options for a normal deck
                val deckOptionsNormal = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.DECK_OPTIONS, didA)
                assertEquals("com.ichi2.anki.SingleFragmentActivity", deckOptionsNormal.component!!.className)
                onBackPressedDispatcher.onBackPressed()

                // select deck options for a dynamic deck
                val deckOptionsDynamic = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.DECK_OPTIONS, didDynamicA)
                assertEquals("com.ichi2.anki.FilteredDeckOptions", deckOptionsDynamic.component!!.className)
                onBackPressedDispatcher.onBackPressed()

                Prefs.newReviewRemindersEnabled = true
                val scheduleReminders = selectContextMenuOptionForActivity(DeckPickerContextMenuOption.SCHEDULE_REMINDERS, didA)
                assertEquals("com.ichi2.anki.SingleFragmentActivity", scheduleReminders.component!!.className)
                onBackPressedDispatcher.onBackPressed()
            }
        }

    @Test
    fun `ContextMenu deletes deck when selecting DELETE_DECK`() =
        runTest {
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
                val didA = addDeck("Deck 1")
                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.DELETE_DECK, didA)
                assertThat(getColUnsafe.decks.allNamesAndIds().map { it.id }, not(containsInAnyOrder(didA)))
            }
        }

    @Test
    fun `ContextMenu creates deck shortcut when selecting CREATE_SHORTCUT`() =
        runTest {
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
                val didA = addDeck("Deck 1")
                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.CREATE_SHORTCUT, didA)
                assertEquals(
                    "Deck 1",
                    ShortcutManagerCompat.getShortcuts(this, ShortcutManagerCompat.FLAG_MATCH_PINNED).first().shortLabel,
                )
            }
        }

    @Test
    @Flaky(OS.WINDOWS)
    fun `ContextMenu unburied cards when selecting UNBURY`() =
        runTest {
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
                TimeManager.reset()
                // stop 'next day' code running, which calls 'unbury'
                updateDeckList()
                val deckId = addDeck("Deck 1")
                getColUnsafe.decks.select(deckId)
                getColUnsafe.notetypes.byName("Basic")!!.did = deckId
                val card = addBasicNote("front", "back").firstCard()
                getColUnsafe.sched.buryCards(listOf(card.id))
                updateDeckList()
                advanceRobolectricLooper()
                assertEquals(1, visibleDeckCount)
                assertTrue(getColUnsafe.sched.haveBuried(), "Deck should have buried cards")
                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.UNBURY, deckId)
                kotlin.test.assertFalse(getColUnsafe.sched.haveBuried())
            }
        }

    @Test
    fun `ContextMenu testDynRebuildAndEmpty`() =
        runTest {
            startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).run {
                val cardIds =
                    (0..3)
                        .map { addBasicNote("$it", "").firstCard().id }
                assertTrue(allCardsInSameDeck(cardIds, 1))
                val deckId = addDynamicDeck("Deck 1")
                getColUnsafe.sched.rebuildDyn(deckId)
                assertTrue(allCardsInSameDeck(cardIds, deckId))
                updateDeckList()
                assertEquals(1, visibleDeckCount)

                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY, deckId) // Empty

                assertTrue(allCardsInSameDeck(cardIds, 1))

                supportFragmentManager.selectContextMenuOption(DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD, deckId) // Rebuild

                assertTrue(allCardsInSameDeck(cardIds, deckId))
            }
        }

    private fun allCardsInSameDeck(
        cardIds: List<Long>,
        deckId: DeckId,
    ): Boolean = cardIds.all { col.getCard(it).did == deckId }

    @Test
    fun checkDisplayOfStudyOptionsOnTablet() {
        assumeTrue("We are running on a tablet", mQualifiers!!.contains("xlarge"))
        val deckPickerEx =
            super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java,
                Intent(),
            )
        val studyOptionsFragment =
            deckPickerEx.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) as StudyOptionsFragment?
        assertThat(
            "Study options should show on start on tablet",
            studyOptionsFragment,
            notNullValue(),
        )
    }

    @Test
    fun checkIfReturnsTrueWhenAtLeastOneDeckIsDisplayed() {
        addDeck("Hello World")
        // Reason for using 2 as the number of decks -> This deck + Default deck
        assertThat("Deck added", col.decks.count(), equalTo(2))
        val deckPicker =
            startActivityNormallyOpenCollectionWithIntent(
                DeckPicker::class.java,
                Intent(),
            )
        assertThat(
            "Deck is being displayed",
            deckPicker.hasAtLeastOneDeckBeingDisplayed(),
            equalTo(true),
        )
    }

    @Test
    fun checkIfReturnsFalseWhenNoDeckIsDisplayed() {
        // Only default deck would be there in the count, hence using the value as 1.
        // Default deck does not get displayed in the DeckPicker if the default deck is empty.
        assertThat("Contains only default deck", col.decks.count(), equalTo(1))
        val deckPicker =
            startActivityNormallyOpenCollectionWithIntent(
                DeckPicker::class.java,
                Intent(),
            )
        assertThat(
            "No deck is being displayed",
            deckPicker.hasAtLeastOneDeckBeingDisplayed(),
            equalTo(false),
        )
    }

    @Test
    fun `unbury is usable - Issue 15050`() {
        // We had an issue where 'Unbury' was not visible
        // This was because the deck selection was not changed when a long press occurred

        // one empty deck to be initially selected, one with cards to check 'unbury' status
        val emptyDeck = addDeck("No Cards")
        val deckWithCards = addDeck("With Cards")
        updateDeckConfig(deckWithCards) { new.bury = true }

        // Add a note with 2 cards in deck "With Cards", one of these cards is to be buried
        col.notetypes.byName("Basic (and reversed card)")!!.also { noteType ->
            col.notetypes.save(noteType.apply { did = deckWithCards })
        }
        addBasicAndReversedNote()

        // Answer 'Easy' for one of the cards, burying the other
        col.decks.select(deckWithCards)
        col.sched.deckDueTree() // ? if not called, decks.select(toSelect) un-buries a card
        col.sched.answerCard(col.sched.card!!, Rating.EASY)
        assertThat("the other card is buried", col.sched.card, nullValue())

        // select a deck with no cards
        col.decks.select(emptyDeck)
        assertThat("unbury is not visible: deck has no cards", !col.sched.haveBuried())

        deckPicker {
            assertThat("deck focus is set", viewModel.focusedDeck, equalTo(emptyDeck))

            // ACT: open up the Deck Context Menu
            val deckToClick =
                recyclerView.children.single {
                    it.findViewById<TextView>(R.id.deckpicker_name).text == "With Cards"
                }
            deckToClick.performLongClick()

            // ASSERT
            assertThat("unbury is visible: one card is buried", col.sched.haveBuried())
            assertThat("deck focus has changed", viewModel.focusedDeck, equalTo(deckWithCards))
        }
    }

    @Test
    @NeedsTest("possible bug: Moving the ops outside the deckPicker { } failed in tablet mode")
    fun `undo menu item changes`() =
        runTest {
            fun DeckPicker.getUndoTitle() = menu().findItem(R.id.action_undo).title.toString()

            fun waitForMenu() = ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            suspend fun DeckPicker.undo() {
                undoAndShowSnackbar()
                waitForMenu()
            }

            deckPicker {
                // enqueue two actions, neither of which affect the study queues
                val note = addBasicNoteWithOp()
                note.updateOp { this.fields[0] = "baz" }

                waitForMenu()
                assertThat(getUndoTitle(), containsString("Update Note"))
                undo()
                assertThat(getUndoTitle(), containsString("Add Note"))
            }
        }

    private fun deckPicker(function: suspend DeckPicker.() -> Unit) =
        runTest {
            val deckPicker =
                startActivityNormallyOpenCollectionWithIntent(
                    DeckPicker::class.java,
                    Intent(),
                )
            function(deckPicker)
        }

    enum class CollectionType(
        val assetFile: String,
        private val deckName: String,
    ) {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16"),
        SCHEMA_V_250(
            "schema250.anki2",
            "ThisIsSchema250",
        ),
        ;

        fun isCollection(col: com.ichi2.anki.libanki.Collection): Boolean = col.decks.byName(deckName) != null
    }

    internal class DeckPickerEx : DeckPicker() {
        var databaseErrorDialog: DatabaseErrorDialogType? = null
        var displayedAnalyticsOptIn = false
        var optionsMenu: Menu? = null

        override fun showDatabaseErrorDialog(
            errorDialogType: DatabaseErrorDialogType,
            exceptionData: DatabaseErrorDialog.CustomExceptionData?,
        ) {
            databaseErrorDialog = errorDialogType
        }

        fun onStoragePermissionGranted() {
            onRequestPermissionsResult(
                REQUEST_STORAGE_PERMISSION,
                arrayOf(""),
                intArrayOf(PackageManager.PERMISSION_GRANTED),
            )
        }

        override fun displayAnalyticsOptInDialog() {
            displayedAnalyticsOptIn = true
            super.displayAnalyticsOptInDialog()
        }

        override fun onPrepareOptionsMenu(menu: Menu): Boolean {
            optionsMenu = menu
            return super.onPrepareOptionsMenu(menu)
        }
    }
}
