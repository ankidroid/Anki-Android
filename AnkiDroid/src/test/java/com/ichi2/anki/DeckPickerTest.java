package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowStatFs;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class DeckPickerTest extends RobolectricTest {

    private ActivityController deckPickerController;

    private TestDeckPicker deckPicker;

    private ShadowActivity shadowDeckPicker;

    @Before
    public void setUp() {

        super.setUp();

        // Create the DeckPicker and make sure it has write permission so the Collection may be opened
        deckPickerController = Robolectric.buildActivity(TestDeckPicker.class);
        deckPicker = (TestDeckPicker) deckPickerController.get();
        shadowDeckPicker = Shadows.shadowOf(deckPicker);
        shadowDeckPicker.grantPermissions("android.permission.WRITE_EXTERNAL_STORAGE");

        // Make sure the Backup system thinks it has enough space so startup isn't blocked
        String backupPath = new File(CollectionHelper.getCurrentAnkiDroidDirectory(getTargetContext())).getParent();
        ShadowStatFs.registerStats(backupPath, 10 * 1024 * 1024, 10 * 1024 * 1024, 10 * 1024 * 1024);

        // Make sure that we are clean to start
        Assert.assertFalse("startup screens already displayed?", deckPicker.startupScreensDisplayed);
        Assert.assertFalse("startup already finished?", deckPicker.finishedStartup);
    }

    @Test
    public void verifyCodeMessages() {

        Map<Integer, String> mCodeResponsePairs = new HashMap<>();
        final Context context = ApplicationProvider.getApplicationContext();
        mCodeResponsePairs.put(407, context.getString(R.string.sync_error_407_proxy_required));
        mCodeResponsePairs.put(409, context.getString(R.string.sync_error_409));
        mCodeResponsePairs.put(413, context.getString(R.string.sync_error_413_collection_size));
        mCodeResponsePairs.put(500, context.getString(R.string.sync_error_500_unknown));
        mCodeResponsePairs.put(501, context.getString(R.string.sync_error_501_upgrade_required));
        mCodeResponsePairs.put(502, context.getString(R.string.sync_error_502_maintenance));
        mCodeResponsePairs.put(503, context.getString(R.string.sync_too_busy));
        mCodeResponsePairs.put(504, context.getString(R.string.sync_error_504_gateway_timeout));

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                for (Map.Entry<Integer, String> entry : mCodeResponsePairs.entrySet()) {
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
    public void verifyReleaseUpgrade() {

        // Pretend we are 9.9.0, going to 9.9.1 and make sure we get an Info popup
        // This may break at some point if we get past version 9.9.0 with the same naming scheme
        prepareVersion(90901300, "9.9.1", 90902300, "9.9.2");
        deckPickerController.create();
        assertState(
                deckPicker.startupScreensDisplayed,  // we should be called
                !deckPicker.mRecommendFullSync,      // we should not recommend a sync
                !deckPicker.prefsUpgraded,           // no prefs upgrade
                !deckPicker.integrityChecked,        // no integrity check
                !deckPicker.finishedStartup,          // we not should finish startup
                !deckPicker.activityRestarted,       // we should not restart
                Info.class, shadowDeckPicker.getNextStartedActivity(),  // Info intent
                "9.9.1");                  // we have a 9.9.1 last version
    }


    @Test
    public void verifyFreshInstall() {

        // Pretend we are a fresh 2.9.1 install
        prepareVersion(0, "", 20901300, "2.9.1");
        deckPickerController.create();
        assertState(
                deckPicker.startupScreensDisplayed,  // we should be called
                !deckPicker.mRecommendFullSync,      // we should not recommend a sync
                !deckPicker.prefsUpgraded,           // no prefs upgrade
                !deckPicker.integrityChecked,        // no integrity check
                deckPicker.finishedStartup,          // we should finish startup
                !deckPicker.activityRestarted,       // we should not restart
                null, shadowDeckPicker.getNextStartedActivity(),  // no intent
                "2.9.1");                  // we have a 2.9.1 last version
    }

    @Test
    public void verifyNoUpgrade() {

        // Set things up so it looks like a simple app restart on same versions
        prepareVersion(20901300, "2.9.1", 20901300, "2.9.1");
        deckPickerController.create();
        assertState(
                deckPicker.startupScreensDisplayed,  // we should be called
                !deckPicker.mRecommendFullSync,      // we should not recommend a sync
                !deckPicker.prefsUpgraded,           // no prefs upgrade
                !deckPicker.integrityChecked,        // no integrity check
                deckPicker.finishedStartup,          // we should finish startup
                !deckPicker.activityRestarted,       // we should not restart
                null, shadowDeckPicker.getNextStartedActivity(),  // no intent
                "2.9.1");                  // we have a 2.9.1 last version
    }


    @Test
    public void verifyPrefsNoDatabaseUpgrade() {

        // Set things up so it looks like it would trigger a prefs upgrade but no database check
        deckPicker.prefCheckVersion = 20902300;
        deckPicker.dbCheckVersion = 20901300;
        prepareVersion(20901300, "2.9.1", 20902300, "2.9.2");
        deckPickerController.create();
        assertState(
                deckPicker.startupScreensDisplayed,  // we should be called
                !deckPicker.mRecommendFullSync,      // we should not recommend a sync
                deckPicker.prefsUpgraded,            // prefs should upgrade
                !deckPicker.integrityChecked,        // no integrity check
                !deckPicker.finishedStartup,         // we should not finish startup
                deckPicker.activityRestarted,        // we should restart
                TestDeckPicker.class, shadowDeckPicker.getNextStartedActivity(),  // restart is ourselves
                "2.9.1");                  // we have a 2.9.1 last version
    }


    @Test
    public void verifyInterruptingUpgrade() {

        // Pretend we are before 2.3.0beta, and across to 2.9.2, verify full sync and integrity check
        prepareVersion(20201300, "2.2.1", 20902300, "2.9.2");
        deckPickerController.create();
        assertState(
                deckPicker.startupScreensDisplayed, // method call should work
                deckPicker.mRecommendFullSync,      // this was old enough full sync should be triggered
                deckPicker.prefsUpgraded,           // old enough to upgrade prefs
                deckPicker.integrityChecked,        // old enough to check integrity
                !deckPicker.finishedStartup,        // startup won't finish because of the other work
                !deckPicker.activityRestarted,      // activity doesn't restart because integrity check hijacks
                null, shadowDeckPicker.getNextStartedActivity(),       // no intents should start
                "");                      // no one puts a lastVersion yet
    }


    private void assertState(boolean startupScreens, boolean fullSync, boolean prefsUpgrade, boolean integrity,
                             boolean finished, boolean restart, Class intentWanted, Intent intent, String lastVersion) {
        Assert.assertTrue("startup screen state incorrect", startupScreens);
        Assert.assertTrue("full sync state incorrect", fullSync);
        Assert.assertTrue("prefs upgrade state incorrect", prefsUpgrade);
        Assert.assertTrue("integrity check state incorrect", integrity);
        Assert.assertTrue("startup finish state incorrect", finished);
        Assert.assertTrue("restart state incorrect", restart);
        if (intentWanted == null) {
            Assert.assertNull("should not have started an Intent", intent);
        } else {
            Assert.assertEquals("Wrong intent started", intentWanted, Shadows.shadowOf(intent).getIntentClass());
        }
        Assert.assertEquals("lastVersion set incorrectly", lastVersion, AnkiDroidApp.getSharedPrefs(getTargetContext()).getString("lastVersion", ""));
    }


    private void prepareVersion(int prevCode, String prevName, int curCode, String curName) {
        SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(getTargetContext());
        prefs.edit().putInt("lastUpgradeVersion", prevCode).apply();
        prefs.edit().putString("lastVersion", prevName).apply();
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(getTargetContext().getPackageManager());
        PackageInfo ankiPackageInfo = shadowPackageManager.getInternalMutablePackageInfo(getTargetContext().getPackageName());
        ankiPackageInfo.setLongVersionCode(curCode);
        ankiPackageInfo.versionName = curName;
    }
}
