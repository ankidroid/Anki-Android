// noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.Menu
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Storage
import com.ichi2.libanki.exception.UnknownDatabaseVersionException
import com.ichi2.testutils.AnkiActivityUtils
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.DbUtils
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.ResourceLoader
import net.ankiweb.rsdroid.BackendFactory
import org.apache.commons.exec.OS
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(ParameterizedRobolectricTestRunner::class)
@KotlinCleanup("fix IDE lint issues")
@KotlinCleanup("replace `when` usages")
@KotlinCleanup("`is` => equalTo")
class DeckPickerTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var mQualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic
        fun initParameters(): Collection<String> {
            return Arrays.asList("normal", "xlarge")
        }
    }

    @Before
    fun before() {
        RuntimeEnvironment.setQualifiers(mQualifiers)
    }

    @Test
    fun verifyCodeMessages() {
        @KotlinCleanup("use scope function")
        val codeResponsePairs: MutableMap<Int, String> = HashMap()
        val context = targetContext
        codeResponsePairs[407] = context.getString(R.string.sync_error_407_proxy_required)
        codeResponsePairs[409] = context.getString(R.string.sync_error_409)
        codeResponsePairs[413] = context.getString(R.string.sync_error_413_collection_size)
        codeResponsePairs[500] = context.getString(R.string.sync_error_500_unknown)
        codeResponsePairs[501] = context.getString(R.string.sync_error_501_upgrade_required)
        codeResponsePairs[502] = context.getString(R.string.sync_error_502_maintenance)
        codeResponsePairs[503] = context.getString(R.string.sync_too_busy)
        codeResponsePairs[504] = context.getString(R.string.sync_error_504_gateway_timeout)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                for ((key, value) in codeResponsePairs) {
                    assertEquals(deckPicker.rewriteError(key), value)
                }
            }
        }
    }

    @Test
    fun verifyBadCodesNoMessage() {
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            scenario.onActivity { deckPicker: DeckPicker ->
                assertNull(deckPicker.rewriteError(0))
                assertNull(deckPicker.rewriteError(-1))
                assertNull(deckPicker.rewriteError(1))
                assertNull(deckPicker.rewriteError(Int.MIN_VALUE))
                assertNull(deckPicker.rewriteError(Int.MAX_VALUE))
            }
        }
    }

    @Test
    fun getPreviousVersionUpgradeFrom201to292() {
        val newVersion = 20900302 // 2.9.2
        val preferences = mock(SharedPreferences::class.java)
        `when`(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion.toLong()))
            .thenThrow(ClassCastException::class.java)
        `when`(preferences.getInt(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        `when`(preferences.getString(DeckPicker.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.1")
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        `when`(editor.remove(DeckPicker.UPGRADE_VERSION_KEY)).thenReturn(updated)
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
        `when`(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        `when`(preferences.getInt(DeckPicker.UPGRADE_VERSION_KEY, 20900203))
            .thenThrow(ClassCastException::class.java)
        `when`(preferences.getString(DeckPicker.UPGRADE_VERSION_KEY, ""))
            .thenReturn("2.0.2")
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        `when`(editor.remove(DeckPicker.UPGRADE_VERSION_KEY)).thenReturn(updated)
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
        `when`(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenThrow(ClassCastException::class.java)
        `when`(preferences.getInt(DeckPicker.UPGRADE_VERSION_KEY, 20900203))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
        val updated = mock(SharedPreferences.Editor::class.java)
        `when`(editor.remove(DeckPicker.UPGRADE_VERSION_KEY)).thenReturn(updated)
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
        `when`(preferences.getLong(DeckPicker.UPGRADE_VERSION_KEY, newVersion))
            .thenReturn(prevVersion)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)
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
            addNoteUsingBasicModel("Which card is this ?", Integer.toString(i))
        }
        // This set a card as current card
        sched.card
        ensureCollectionLoadIsSynchronous()
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(
            DeckPicker::class.java, Intent()
        )
        assertEquals(10, deckPicker.mDueTree!![0].value.newCount.toLong())
    }

    @Test
    fun confirmDeckDeletionDeletesEmptyDeck() {
        val did = addDeck("Hello World")
        assertThat("Deck was added", col.decks.count(), `is`(2))
        val deckPicker = startActivityNormallyOpenCollectionWithIntent(
            DeckPicker::class.java, Intent()
        )
        awaitJob(deckPicker.confirmDeckDeletion(did))
        advanceRobolectricLooperWithSleep()
        assertThat("deck was deleted", col.decks.count(), `is`(1))
    }

    @Test
    fun deletion_of_filtered_deck_shows_warning_issue_10238() {
        if (!BackendFactory.defaultLegacySchema) {
            // undoable
            return
        }
        // Filtered decks contain their own options, deleting one can cause a significant loss of work.
        // And they are more likely to be empty temporarily
        val did = addDynamicDeck("filtered")
        val deckPicker = startActivityNormallyOpenCollectionWithIntent(
            DeckPicker::class.java, Intent()
        )
        deckPicker.confirmDeckDeletion(did)
        val fragment = AnkiActivityUtils.getDialogFragment(deckPicker)
        assertThat(
            "deck deletion confirmation window should be shown", fragment,
            instanceOf(DeckPickerConfirmDeleteDeckDialog::class.java)
        )
    }

    @Test
    fun databaseLockedTest() {
        // don't call .onCreate
        val deckPicker = Robolectric.buildActivity(DeckPickerEx::class.java, Intent()).get()
        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DATABASE_LOCKED)
        assertThat(
            deckPicker.databaseErrorDialog,
            `is`(DatabaseErrorDialog.DIALOG_DB_LOCKED)
        )
    }

    @Test
    fun databaseLockedWithPermissionIntegrationTest() {
        AnkiDroidApp.sSentExceptionReportHack = false
        try {
            BackendEmulatingOpenConflict.enable()
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            assertThat(
                "A specific dialog for a conflict should be shown",
                d.databaseErrorDialog,
                `is`(DatabaseErrorDialog.DIALOG_DB_LOCKED)
            )
            assertThat(
                "No exception reports should be thrown",
                AnkiDroidApp.sSentExceptionReportHack,
                `is`(false)
            )
        } finally {
            BackendEmulatingOpenConflict.disable()
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    @RunInBackground
    fun databaseLockedNoPermissionIntegrationTest() {
        // no permissions -> grant permissions -> db locked
        try {
            InitialActivityWithConflictTest.setupForDefault()
            BackendEmulatingOpenConflict.enable()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )

            // grant permissions
            InitialActivityWithConflictTest.setupForDatabaseConflict()
            d.onStoragePermissionGranted()
            assertThat(
                "A specific dialog for a conflict should be shown",
                d.databaseErrorDialog,
                `is`(DatabaseErrorDialog.DIALOG_DB_LOCKED)
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
            InitialActivityWithConflictTest.grantWritePermissions()
            BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
            // We don't show it if the user is new.
            AnkiDroidApp.getSharedPrefs(targetContext).edit().putString("lastVersion", "0.1")
                .apply()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            assertThat(
                "Analytics opt-in should be displayed",
                d.displayedAnalyticsOptIn,
                `is`(true)
            )
        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions()
            BackupManagerTestUtilities.reset()
        }
    }

    @Test
    @RunInBackground
    fun doNotShowOptionsMenuWhenCollectionInaccessible() {
        try {
            enableNullCollection()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            assertThat(
                "Options menu not displayed when collection is inaccessible",
                d.prepareOptionsMenu,
                `is`(false)
            )
        } finally {
            disableNullCollection()
        }
    }

    @Test
    fun showOptionsMenuWhenCollectionAccessible() {
        try {
            InitialActivityWithConflictTest.grantWritePermissions()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            assertThat(
                "Options menu is displayed when collection is accessible",
                d.prepareOptionsMenu,
                `is`(true)
            )
        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions()
        }
    }

    @Test
    @RunInBackground
    fun doNotShowSyncBadgeWhenCollectionInaccessible() {
        try {
            enableNullCollection()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            assertThat(
                "Sync badge is not displayed when collection is inaccessible",
                d.displaySyncBadge,
                `is`(false)
            )
        } finally {
            disableNullCollection()
        }
    }

    @Test
    fun showSyncBadgeWhenCollectionAccessible() {
        try {
            InitialActivityWithConflictTest.grantWritePermissions()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            assertThat(
                "Sync badge is displayed when collection is accessible",
                d.displaySyncBadge,
                `is`(true)
            )
        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions()
        }
    }

    @Test
    @RunInBackground
    fun onResumeLoadCollectionFailureWithInaccessibleCollection() {
        try {
            InitialActivityWithConflictTest.revokeWritePermissions()
            enableNullCollection()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )

            // Neither collection, not its models will be initialized without storage permission
            assertThat(
                "Lazy Collection initialization CollectionTask.LoadCollectionComplete fails",
                d.col,
                `is`(
                    nullValue()
                )
            )
        } finally {
            disableNullCollection()
        }
    }

    @Test
    fun onResumeLoadCollectionSuccessWithAccessibleCollection() {
        try {
            InitialActivityWithConflictTest.grantWritePermissions()
            val d = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            assertThat(
                "Collection initialization ensured by CollectionTask.LoadCollectionComplete",
                d.col,
                `is`(
                    notNullValue()
                )
            )
            assertThat(
                "Collection Models Loaded", d.col.models,
                `is`(
                    notNullValue()
                )
            )
        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions()
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
            val deckPicker: DeckPicker = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            waitForAsyncTasksToComplete()
            assertThat(
                "Collection should now be open",
                CollectionHelper.getInstance().colIsOpen()
            )
            assertThat(
                CollectionType.SCHEMA_V_16.isCollection(
                    col
                ),
                `is`(true)
            )
            assertThat(
                "Decks should be visible",
                deckPicker.visibleDeckCount,
                `is`(1)
            )
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun corruptVersion16CollectionShowsDatabaseError() {
        try {
            setupColV16()

            // corrupt col
            DbUtils.performQuery(targetContext, "drop table decks")
            InitialActivityWithConflictTest.setupForValid(targetContext)
            val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            waitForAsyncTasksToComplete()
            assertThat(
                "Collection should not be open",
                !CollectionHelper.getInstance().colIsOpen()
            )
            assertThat(
                "An error dialog should be displayed",
                deckPicker.databaseErrorDialog,
                `is`(DatabaseErrorDialog.DIALOG_LOAD_FAILED)
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
            val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPickerEx::class.java, Intent()
            )
            waitForAsyncTasksToComplete()
            assertThat(
                "Collection should not be open",
                !CollectionHelper.getInstance().colIsOpen()
            )
            assertThat(
                "An error dialog should be displayed",
                deckPicker.databaseErrorDialog,
                `is`(DatabaseErrorDialog.INCOMPATIBLE_DB_VERSION)
            )
            assertThat(
                CollectionHelper.getDatabaseVersion(targetContext),
                `is`(250)
            )
        } catch (e: UnknownDatabaseVersionException) {
            assertThat("no exception should be thrown", false, `is`(true))
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun checkDisplayOfStudyOptionsOnTablet() {
        assumeTrue("We are running on a tablet", mQualifiers!!.contains("xlarge"))
        val deckPickerEx = super.startActivityNormallyOpenCollectionWithIntent(
            DeckPickerEx::class.java, Intent()
        )
        val studyOptionsFragment =
            deckPickerEx.supportFragmentManager.findFragmentById(R.id.studyoptions_fragment) as StudyOptionsFragment?
        assertThat(
            "Study options should show on start on tablet",
            studyOptionsFragment,
            notNullValue()
        )
    }

    @Test
    fun checkIfReturnsTrueWhenAtLeastOneDeckIsDisplayed() {
        addDeck("Hello World")
        // Reason for using 2 as the number of decks -> This deck + Default deck
        assertThat("Deck added", col.decks.count(), `is`(2))
        val deckPicker = startActivityNormallyOpenCollectionWithIntent(
            DeckPicker::class.java, Intent()
        )
        assertThat(
            "Deck is being displayed",
            deckPicker.hasAtLeastOneDeckBeingDisplayed(),
            `is`(true)
        )
    }

    @Test
    fun checkIfReturnsFalseWhenNoDeckIsDisplayed() {
        // Only default deck would be there in the count, hence using the value as 1.
        // Default deck does not get displayed in the DeckPicker if the default deck is empty.
        assertThat("Contains only default deck", col.decks.count(), `is`(1))
        val deckPicker = startActivityNormallyOpenCollectionWithIntent(
            DeckPicker::class.java, Intent()
        )
        assertThat(
            "No deck is being displayed",
            deckPicker.hasAtLeastOneDeckBeingDisplayed(),
            `is`(false)
        )
    }

    private fun useCollection(collectionType: CollectionType) {
        // load asset into temp
        val path = ResourceLoader.getTempCollection(targetContext, collectionType.assetFile)
        val p = File(path)
        assertThat(p.isFile, `is`(true))
        val collectionDirectory = p.parent

        // set collection path
        @KotlinCleanup("use prefs.edit{}")
        val preferences = AnkiDroidApp.getSharedPrefs(targetContext)
        preferences.edit().putString(CollectionHelper.PREF_COLLECTION_PATH, collectionDirectory)
            .apply()

        // ensure collection not loaded yet
        assertThat(
            "collection should not be loaded",
            CollectionHelper.getInstance().colIsOpen(),
            `is`(false)
        )
    }

    protected fun setupColV16() {
        Storage.setUseInMemory(false)
        useCollection(CollectionType.SCHEMA_V_16)
    }

    protected fun setupColV250() {
        Storage.setUseInMemory(false)
        useCollection(CollectionType.SCHEMA_V_250)
    }

    enum class CollectionType(val assetFile: String, private val deckName: String) {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16"), SCHEMA_V_250(
            "schema250.anki2",
            "ThisIsSchema250"
        );

        fun isCollection(col: com.ichi2.libanki.Collection): Boolean {
            return col.decks.allNames().contains(deckName)
        }
    }

    private class DeckPickerEx : DeckPicker() {
        var databaseErrorDialog = 0
        var displayedAnalyticsOptIn = false
        var prepareOptionsMenu = false
        var displaySyncBadge = false

        override fun showDatabaseErrorDialog(id: Int) {
            databaseErrorDialog = id
        }

        fun onStoragePermissionGranted() {
            onRequestPermissionsResult(
                REQUEST_STORAGE_PERMISSION,
                arrayOf(""),
                intArrayOf(PackageManager.PERMISSION_GRANTED)
            )
        }

        override fun displayAnalyticsOptInDialog() {
            displayedAnalyticsOptIn = true
            super.displayAnalyticsOptInDialog()
        }

        override fun onPrepareOptionsMenu(menu: Menu): Boolean {
            prepareOptionsMenu = super.onPrepareOptionsMenu(menu)
            return prepareOptionsMenu
        }

        override fun displaySyncBadge(menu: Menu) {
            displaySyncBadge = true
            super.displaySyncBadge(menu)
        }
    }
}
