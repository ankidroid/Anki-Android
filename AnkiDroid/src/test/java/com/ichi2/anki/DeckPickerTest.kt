// noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.Menu
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.exception.UnknownDatabaseVersionException
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Storage
import com.ichi2.testutils.*
import com.ichi2.utils.ResourceLoader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.commons.exec.OS
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var mQualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<String> {
            return listOf("normal", "xlarge")
        }
    }

    @Before
    fun before() {
        RuntimeEnvironment.setQualifiers(mQualifiers)
        getPreferences().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
        }
    }

    @Test
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
        val col = col
        val sched = col.sched
        val dconf = col.decks.getConf(1)
        assertNotNull(dconf)
        dconf.getJSONObject("new").put("perDay", 10)
        col.decks.save(dconf)
        for (i in 0..10) {
            addNoteUsingBasicModel("Which card is this ?", i.toString())
        }
        // This set a card as current card
        sched.card
        ensureCollectionLoadIsSynchronous()
        val deckPicker =
            super.startActivityNormallyOpenCollectionWithIntent(
                DeckPicker::class.java,
                Intent(),
            )
        assertEquals(10, deckPicker.dueTree!!.children[0].newCount.toLong())
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
            deckPicker.confirmDeckDeletion(did)
            advanceRobolectricLooperWithSleep()
            assertThat("deck was deleted", col.decks.count(), equalTo(1))
        }

    @Test
    fun databaseLockedTest() {
        // don't call .onCreate
        val deckPicker = Robolectric.buildActivity(DeckPickerEx::class.java, Intent()).get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DATABASE_LOCKED)
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
    @RunInBackground
    @Ignore("Flaky. Try to unflak when AsyncTask is entirely removed.")
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
            targetContext.sharedPrefs().edit().putString("lastVersion", "0.1")
                .apply()
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
    @RunInBackground
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
    @RunInBackground
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
    @RunInBackground
    @NeedsTest("fix this on Windows")
    fun version16CollectionOpens() {
        if (OS.isFamilyWindows()) {
            assumeTrue("test is flaky on Windows", false)
        }
        try {
            setupColV16()
            InitialActivityWithConflictTest.setupForValid(targetContext)
            val deckPicker: DeckPicker =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            waitForAsyncTasksToComplete()
            assertThat(
                "Collection should now be open",
                CollectionHelper.instance.colIsOpenUnsafe(),
            )
            assertThat(
                CollectionType.SCHEMA_V_16.isCollection(
                    col,
                ),
                equalTo(true),
            )
            assertThat(
                "Decks should be visible",
                deckPicker.visibleDeckCount,
                equalTo(1),
            )
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Ignore("needs refactoring")
    @Test
    fun corruptVersion16CollectionShowsDatabaseError() {
        try {
            setupColV16()

            // corrupt col
            DbUtils.performQuery(targetContext, "drop table decks")
            InitialActivityWithConflictTest.setupForValid(targetContext)
            val deckPicker =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            waitForAsyncTasksToComplete()
            assertThat(
                "Collection should not be open",
                !CollectionHelper.instance.colIsOpenUnsafe(),
            )
            assertThat(
                "An error dialog should be displayed",
                deckPicker.databaseErrorDialog,
                equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED),
            )
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun futureSchemaShowsError() {
        try {
            setupColV250()
            InitialActivityWithConflictTest.setupForValid(targetContext)
            val deckPicker =
                super.startActivityNormallyOpenCollectionWithIntent(
                    DeckPickerEx::class.java,
                    Intent(),
                )
            waitForAsyncTasksToComplete()
            assertThat(
                "Collection should not be open",
                !CollectionHelper.instance.colIsOpenUnsafe(),
            )
            assertThat(
                "An error dialog should be displayed",
                deckPicker.databaseErrorDialog,
                equalTo(DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION),
            )
            assertThat(
                CollectionHelper.getDatabaseVersion(targetContext),
                equalTo(250),
            )
        } catch (e: UnknownDatabaseVersionException) {
            assertThat("no exception should be thrown", false, equalTo(true))
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

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

    private fun useCollection(collectionType: CollectionType) {
        // load asset into temp
        val path = ResourceLoader.getTempCollection(targetContext, collectionType.assetFile)
        val p = File(path)
        assertThat(p.isFile, equalTo(true))
        val collectionDirectory = p.parent

        // set collection path
        targetContext.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, collectionDirectory)
        }

        // ensure collection not loaded yet
        assertThat(
            "collection should not be loaded",
            CollectionHelper.instance.colIsOpenUnsafe(),
            equalTo(false),
        )
    }

    private fun setupColV16() {
        Storage.setUseInMemory(false)
        useCollection(CollectionType.SCHEMA_V_16)
    }

    private fun setupColV250() {
        Storage.setUseInMemory(false)
        useCollection(CollectionType.SCHEMA_V_250)
    }

    enum class CollectionType(val assetFile: String, private val deckName: String) {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16"),
        SCHEMA_V_250(
            "schema250.anki2",
            "ThisIsSchema250",
        ),
        ;

        fun isCollection(col: com.ichi2.libanki.Collection): Boolean {
            return col.decks.byName(deckName) != null
        }
    }

    private class DeckPickerEx : DeckPicker() {
        var databaseErrorDialog: DatabaseErrorDialogType? = null
        var displayedAnalyticsOptIn = false
        var optionsMenu: Menu? = null

        override fun showDatabaseErrorDialog(errorDialogType: DatabaseErrorDialogType) {
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
