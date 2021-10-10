//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.view.Menu;

import com.ichi2.anki.dialogs.DatabaseErrorDialog;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.testutils.BackendEmulatingOpenConflict;
import com.ichi2.testutils.BackupManagerTestUtilities;
import com.ichi2.testutils.DbUtils;
import com.ichi2.utils.ResourceLoader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ActivityScenario;

import static com.ichi2.anki.DeckPicker.UPGRADE_VERSION_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class DeckPickerTest extends RobolectricTest {

    @Parameter
    public String mQualifiers;

    @Parameters
    public static java.util.Collection<String> initParameters() {
        return Arrays.asList("normal", "xlarge");
    }

    @Before
    public void before() {
        RuntimeEnvironment.setQualifiers(mQualifiers);
    }

    @Test
    public void verifyCodeMessages() {

        Map<Integer, String> codeResponsePairs = new HashMap<>();
        final Context context = getTargetContext();
        codeResponsePairs.put(407, context.getString(R.string.sync_error_407_proxy_required));
        codeResponsePairs.put(409, context.getString(R.string.sync_error_409));
        codeResponsePairs.put(413, context.getString(R.string.sync_error_413_collection_size));
        codeResponsePairs.put(500, context.getString(R.string.sync_error_500_unknown));
        codeResponsePairs.put(501, context.getString(R.string.sync_error_501_upgrade_required));
        codeResponsePairs.put(502, context.getString(R.string.sync_error_502_maintenance));
        codeResponsePairs.put(503, context.getString(R.string.sync_too_busy));
        codeResponsePairs.put(504, context.getString(R.string.sync_error_504_gateway_timeout));

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                for (Map.Entry<Integer, String> entry : codeResponsePairs.entrySet()) {
                    assertEquals(deckPicker.rewriteError(entry.getKey()), entry.getValue());
                }
            });
        }
    }

    @Test
    public void verifyBadCodesNoMessage() {
        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                assertNull(deckPicker.rewriteError(0));
                assertNull(deckPicker.rewriteError(-1));
                assertNull(deckPicker.rewriteError(1));
                assertNull(deckPicker.rewriteError(Integer.MIN_VALUE));
                assertNull(deckPicker.rewriteError(Integer.MAX_VALUE));
            });
        }
    }

    @Test
    public void getPreviousVersionUpgradeFrom201to292() {
        int newVersion = 20900302; // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getString(UPGRADE_VERSION_KEY, "")).thenReturn("2.0.1");

        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);
        Editor updated = mock(Editor.class);
        when(editor.remove(UPGRADE_VERSION_KEY)).thenReturn(updated);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(0, previousVersion);
            });
        }
        verify(editor, times(1)).remove(UPGRADE_VERSION_KEY);
        verify(updated, times(1)).apply();
    }

    @Test
    public void getPreviousVersionUpgradeFrom202to292() {
        long newVersion = 20900302; // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, 20900203)).thenThrow(ClassCastException.class);
        when(preferences.getString(UPGRADE_VERSION_KEY, "")).thenReturn("2.0.2");

        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);
        Editor updated = mock(Editor.class);
        when(editor.remove(UPGRADE_VERSION_KEY)).thenReturn(updated);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(40, previousVersion);
            });
        }
        verify(editor, times(1)).remove(UPGRADE_VERSION_KEY);
        verify(updated, times(1)).apply();
    }

    @Test
    public void getPreviousVersionUpgradeFrom281to291() {
        int prevVersion = 20800301; // 2.8.1
        long newVersion = 20900301; // 2.9.1

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, 20900203)).thenReturn(prevVersion);

        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);
        Editor updated = mock(Editor.class);
        when(editor.remove(UPGRADE_VERSION_KEY)).thenReturn(updated);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(prevVersion, previousVersion);
            });
        }
        verify(editor, times(1)).remove(UPGRADE_VERSION_KEY);
        verify(updated, times(1)).apply();
    }

    @Test
    public void getPreviousVersionUpgradeFrom291to292() {
        long prevVersion = 20900301; // 2.9.1
        long newVersion = 20900302;  // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenReturn(prevVersion);
        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(prevVersion, previousVersion);
            });
        }
        verify(editor, never()).remove(UPGRADE_VERSION_KEY);
    }

    @Test
    public void limitAppliedAfterReview() {
        Collection col = getCol();
        AbstractSched sched = col.getSched();

        DeckConfig dconf = col.getDecks().getConf(1);
        assertNotNull(dconf);
        dconf.getJSONObject("new").put("perDay", 10);
        col.getDecks().save(dconf);
        for (int i = 0; i < 11; i++) {
            addNoteUsingBasicModel("Which card is this ?", Integer.toString(i));
        }
        // This set a card as current card
        sched.getCard();

        ensureCollectionLoadIsSynchronous();
        DeckPicker deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPicker.class, new Intent());

        assertEquals(10, deckPicker.mDueTree.get(0).getNewCount());
    }

    @Test
    public void confirmDeckDeletionDeletesEmptyDeck() {
        long did = addDeck("Hello World");

        assertThat("Deck was added", getCol().getDecks().count(), is(2));

        DeckPicker deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker.class, new Intent());

        deckPicker.confirmDeckDeletion(did);

        advanceRobolectricLooperWithSleep();

        assertThat("deck was deleted", getCol().getDecks().count(), is(1));
    }

    @Test
    public void databaseLockedTest() {
        // don't call .onCreate
        DeckPickerEx deckPicker = Robolectric.buildActivity(DeckPickerEx.class, new Intent()).get();

        deckPicker.handleStartupFailure(InitialActivity.StartupFailure.DATABASE_LOCKED);

        assertThat(deckPicker.mDatabaseErrorDialog, is(DatabaseErrorDialog.DIALOG_DB_LOCKED));
    }

    @Test
    public void databaseLockedWithPermissionIntegrationTest() {
        AnkiDroidApp.sSentExceptionReportHack = false;
        try {
            BackendEmulatingOpenConflict.enable();
            InitialActivityWithConflictTest.setupForDatabaseConflict();

            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());

            assertThat("A specific dialog for a conflict should be shown", d.mDatabaseErrorDialog, is(DatabaseErrorDialog.DIALOG_DB_LOCKED));

            assertThat("No exception reports should be thrown", AnkiDroidApp.sSentExceptionReportHack, is(false));
        } finally {
            BackendEmulatingOpenConflict.disable();
            InitialActivityWithConflictTest.setupForDefault();
        }
    }

    @Test
    @RunInBackground
    public void databaseLockedNoPermissionIntegrationTest() {
        // no permissions -> grant permissions -> db locked
        try {
            InitialActivityWithConflictTest.setupForDefault();
            BackendEmulatingOpenConflict.enable();

            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());

            // grant permissions
            InitialActivityWithConflictTest.setupForDatabaseConflict();

            d.onStoragePermissionGranted();

            assertThat("A specific dialog for a conflict should be shown", d.mDatabaseErrorDialog, is(DatabaseErrorDialog.DIALOG_DB_LOCKED));
        } finally {
            BackendEmulatingOpenConflict.disable();
            InitialActivityWithConflictTest.setupForDefault();
        }
    }

    @Test
    public void deckPickerOpensWithHelpMakeAnkiDroidBetterDialog() {
        // Refactor: It would be much better to use a spy - see if we can get this into Robolecteic
        try {
            InitialActivityWithConflictTest.grantWritePermissions();
            BackupManagerTestUtilities.setupSpaceForBackup(getTargetContext());
            // We don't show it if the user is new.
            AnkiDroidApp.getSharedPrefs(getTargetContext()).edit().putString("lastVersion", "0.1").apply();

            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());

            assertThat("Analytics opt-in should be displayed", d.mDisplayedAnalyticsOptIn, is(true));

        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions();
            BackupManagerTestUtilities.reset();
        }
    }


    @Test
    @RunInBackground
    public void doNotShowOptionsMenuWhenCollectionInaccessible() {
        try {
            enableNullCollection();
            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());
            assertThat("Options menu not displayed when collection is inaccessible", d.mPrepareOptionsMenu, is(false));
        } finally {
            disableNullCollection();
        }
    }

    @Test
    public void showOptionsMenuWhenCollectionAccessible() {
        try {
            InitialActivityWithConflictTest.grantWritePermissions();
            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());
            assertThat("Options menu is displayed when collection is accessible", d.mPrepareOptionsMenu, is(true));
        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions();
        }
    }

    @Test
    @RunInBackground
    public void doNotShowSyncBadgeWhenCollectionInaccessible() {
        try {
            enableNullCollection();
            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());
            assertThat("Sync badge is not displayed when collection is inaccessible", d.mDisplaySyncBadge, is(false));
        } finally {
            disableNullCollection();
        }
    }

    @Test
    public void showSyncBadgeWhenCollectionAccessible() {
        try {
            InitialActivityWithConflictTest.grantWritePermissions();
            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());
            assertThat("Sync badge is displayed when collection is accessible", d.mDisplaySyncBadge, is(true));
        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions();
        }
    }

    @Test
    @RunInBackground
    public void onResumeLoadCollectionFailureWithInaccessibleCollection() {
        try {
            InitialActivityWithConflictTest.revokeWritePermissions();
            enableNullCollection();
            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());

            // Neither collection, not its models will be initialized without storage permission
            assertThat("Lazy Collection initialization CollectionTask.LoadCollectionComplete fails", d.getCol(), is(nullValue()));
        } finally {
            disableNullCollection();
        }
    }

    @Test
    public void onResumeLoadCollectionSuccessWithAccessibleCollection() {
        try {
            InitialActivityWithConflictTest.grantWritePermissions();
            DeckPickerEx d = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());
            assertThat("Collection initialization ensured by CollectionTask.LoadCollectionComplete", d.getCol(), is(notNullValue()));
            assertThat("Collection Models Loaded", d.getCol().getModels(), is(notNullValue()));
        } finally {
            InitialActivityWithConflictTest.revokeWritePermissions();
        }
    }

    @Test
    @RunInBackground
    public void version16CollectionOpens() {
        try {
            setupColV16();

            InitialActivityWithConflictTest.setupForValid(getTargetContext());

            DeckPicker deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());
            waitForAsyncTasksToComplete();

            assertThat("Collection should now be open", CollectionHelper.getInstance().colIsOpen());

            assertThat(CollectionType.SCHEMA_V_16.isCollection(getCol()), is(true));

            assertThat("Decks should be visible", deckPicker.getDeckCount(), is(1));
        } finally {
            InitialActivityWithConflictTest.setupForDefault();
        }
    }

    @Test
    public void corruptVersion16CollectionShowsDatabaseError() {
        try {
            setupColV16();

            // corrupt col
            DbUtils.performQuery(getTargetContext(), "drop table decks");

            InitialActivityWithConflictTest.setupForValid(getTargetContext());

            DeckPickerEx deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());
            waitForAsyncTasksToComplete();

            assertThat("Collection should not be open", !CollectionHelper.getInstance().colIsOpen());
            assertThat("An error dialog should be displayed", deckPicker.mDatabaseErrorDialog, is(DatabaseErrorDialog.DIALOG_LOAD_FAILED));
        } finally {
            InitialActivityWithConflictTest.setupForDefault();
        }
    }

    @Test
    public void notEnoughSpaceToBackupBeforeDowngradeShowsError() {
        Class<DeckPickerNoSpaceForBackup> clazz = DeckPickerNoSpaceForBackup.class;
        try (MockedStatic<InitialActivity> initialActivityMock = mockStatic(InitialActivity.class, Mockito.CALLS_REAL_METHODS)) {
            initialActivityMock
                .when(() -> InitialActivity.getStartupFailureType(any()))
                .thenAnswer((Answer<InitialActivity.StartupFailure>) invocation -> InitialActivity.StartupFailure.DATABASE_DOWNGRADE_REQUIRED);

            InitialActivityWithConflictTest.setupForValid(getTargetContext());

            DeckPickerNoSpaceForBackup deckPicker = super.startActivityNormallyOpenCollectionWithIntent(clazz, new Intent());

            assertThat("A downgrade failed dialog should be shown", deckPicker.mDisplayedDowngradeFailed, is(true));
        } finally {
            InitialActivityWithConflictTest.setupForDefault();
        }
    }

    @Test
    public void checkDisplayOfStudyOptionsOnTablet() {
        assumeTrue("We are running on a tablet", mQualifiers.contains("xlarge"));
        DeckPickerEx deckPickerEx = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerEx.class, new Intent());

        StudyOptionsFragment studyOptionsFragment = (StudyOptionsFragment) deckPickerEx.getSupportFragmentManager().findFragmentById(R.id.studyoptions_fragment);
        assertThat("Study options should show on start on tablet", studyOptionsFragment, notNullValue());
    }
    
    @Test
    public void checkIfReturnsTrueWhenAtLeastOneDeckIsDisplayed() {
        addDeck("Hello World");
        // Reason for using 2 as the number of decks -> This deck + Default deck
        assertThat("Deck added", getCol().getDecks().count(), is(2));
        DeckPicker deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker.class, new Intent());
        assertThat("Deck is being displayed", deckPicker.hasAtLeastOneDeckBeingDisplayed(), is(true));
    }

    @Test
    public void checkIfReturnsFalseWhenNoDeckIsDisplayed() {
        // Only default deck would be there in the count, hence using the value as 1.
        // Default deck does not get displayed in the DeckPicker if the default deck is empty.
        assertThat("Contains only default deck", getCol().getDecks().count(), is(1));
        DeckPicker deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker.class, new Intent());
        assertThat("No deck is being displayed", deckPicker.hasAtLeastOneDeckBeingDisplayed(), is(false));
    }


    private void useCollection(@SuppressWarnings("SameParameterValue") CollectionType collectionType) {
        // load asset into temp
        String path = ResourceLoader.getTempCollection(getTargetContext(), collectionType.getAssetFile());

        File p = new File(path);
        assertThat(p.isFile(), is(true));
        String collectionDirectory = p.getParent();

        // set collection path
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getTargetContext());
        preferences.edit().putString("deckPath", collectionDirectory).apply();

        // ensure collection not loaded yet
        assertThat("collection should not be loaded", CollectionHelper.getInstance().colIsOpen(), is(false));
    }


    protected void setupColV16() {
        Storage.setUseInMemory(false);
        DB.setSqliteOpenHelperFactory(new FrameworkSQLiteOpenHelperFactory());
        useCollection(CollectionType.SCHEMA_V_16);
    }


    public enum CollectionType {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16");

        private final String mAssetFile;
        private final String mDeckName;


        CollectionType(String s, String deckName) {
            this.mAssetFile = s;
            this.mDeckName = deckName;
        }

        public String getAssetFile() {
            return mAssetFile;
        }


        public boolean isCollection(Collection col) {
            return col.getDecks().allNames().contains(mDeckName);
        }
    }

    private static class DeckPickerNoSpaceForBackup extends DeckPickerEx {

        private boolean mDisplayedDowngradeFailed;


        @Override
        public BackupManager getBackupManager() {
            BackupManager bm = spy(new BackupManager());
            doReturn(false).when(bm).hasFreeDiscSpace(any());
            return bm;
        }


        @Override
        public void displayDowngradeFailedNoSpace() {
            this.mDisplayedDowngradeFailed = true;
            super.displayDowngradeFailedNoSpace();
        }
    }

    private static class DeckPickerEx extends DeckPicker {
        private int mDatabaseErrorDialog;
        private boolean mDisplayedAnalyticsOptIn;
        private boolean mPrepareOptionsMenu;
        private boolean mDisplaySyncBadge = false;


        @Override
        public void showDatabaseErrorDialog(int id) {
            this.mDatabaseErrorDialog = id;
        }

        public void onStoragePermissionGranted() {
            onRequestPermissionsResult(DeckPicker.REQUEST_STORAGE_PERMISSION, new String[] { "" }, new int[] { PackageManager.PERMISSION_GRANTED });
        }


        @Override
        protected void displayAnalyticsOptInDialog() {
            this.mDisplayedAnalyticsOptIn = true;
            super.displayAnalyticsOptInDialog();
        }


        @Override
        public boolean onPrepareOptionsMenu(Menu menu) {
            this.mPrepareOptionsMenu = super.onPrepareOptionsMenu(menu);
            return mPrepareOptionsMenu;
        }

        @Override
        protected void displaySyncBadge(Menu menu) {
            this.mDisplaySyncBadge = true;
            super.displaySyncBadge(menu);
        }
    }
}
