package com.ichi2.anki;

import android.content.SharedPreferences;

// Simple testability overrides and verification toggles
public class TestDeckPicker extends DeckPicker {
    protected boolean startupScreensDisplayed = false;
    protected boolean finishedStartup = false;
    protected boolean integrityChecked = false;
    protected boolean prefsUpgraded = false;
    protected boolean activityRestarted = false;
    protected int prefCheckVersion = -1;
    protected int dbCheckVersion = -1;

    @Override protected void showStartupScreensAndDialogs(SharedPreferences preferences, int skip) {
        startupScreensDisplayed = true;
        super.showStartupScreensAndDialogs(preferences, skip);
    }
    @Override protected void onFinishedStartup() { finishedStartup = true; super.onFinishedStartup(); }
    @Override public void integrityCheck() { integrityChecked = true; super.integrityCheck(); }
    @Override public void upgradePreferences(long prefs) { prefsUpgraded = true; super.upgradePreferences(prefs); }
    @Override public void restartActivity() { activityRestarted = true; super.restartActivity(); }
    @Override protected int getUpgradePrefsVersion() { return prefCheckVersion == -1 ? super.getUpgradePrefsVersion() : prefCheckVersion; }
    @Override protected int getCheckDbAtVersion() { return dbCheckVersion == -1 ? super.getCheckDbAtVersion() : dbCheckVersion; }
}
