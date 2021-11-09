/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;

import android.content.SharedPreferences;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.Collection;

import java.util.function.Supplier;

import androidx.annotation.NonNull;
import timber.log.Timber;

public enum SyncStatus {
    INCONCLUSIVE,
    NO_ACCOUNT,
    NO_CHANGES,
    HAS_CHANGES,
    FULL_SYNC,
    BADGE_DISABLED;

    private static boolean sPauseCheckingDatabase = false;
    private static boolean sMarkedInMemory = false;


    @NonNull
    public static SyncStatus getSyncStatus(@NonNull Supplier<Collection> getCol) {
        Collection col;
        try {
            col = getCol.get();
        } catch (Exception e) {
            Timber.w(e);
            return SyncStatus.INCONCLUSIVE;
        }

        return getSyncStatus(col);
    }


    @NonNull
    public static SyncStatus getSyncStatus(@NonNull Collection col) {
        if (isDisabled()) {
            return SyncStatus.BADGE_DISABLED;
        }

        if (!isLoggedIn()) {
            return SyncStatus.NO_ACCOUNT;
        }

        if (col.schemaChanged()) {
            return SyncStatus.FULL_SYNC;
        }

        if (hasDatabaseChanges()) {
            return SyncStatus.HAS_CHANGES;
        } else {
            return SyncStatus.NO_CHANGES;
        }
    }


    private static boolean isDisabled() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        return !preferences.getBoolean("showSyncStatusBadge", true);
    }


    private static boolean isLoggedIn() {
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance());
        String hkey = preferences.getString("hkey", "");
        return hkey != null && hkey.length() != 0;
    }


    /** Whether data has been changed - to be converted to Rust */
    public static boolean hasDatabaseChanges() {
        return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).getBoolean("changesSinceLastSync", false);
    }

    /** To be converted to Rust */
    public static void markDataAsChanged() {
        if (sPauseCheckingDatabase) {
            return;
        }
        sMarkedInMemory = true;
        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).edit().putBoolean("changesSinceLastSync", true).apply();
    }


    /** To be converted to Rust */
    public static void markSyncCompleted() {
        sMarkedInMemory = false;
        AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).edit().putBoolean("changesSinceLastSync", false).apply();
    }


    public static void ignoreDatabaseModification(@NonNull Runnable runnable) {
        sPauseCheckingDatabase = true;
        try {
            runnable.run();
        } finally {
            sPauseCheckingDatabase = false;
        }
    }

    /** Whether a change in data has been detected - used as a heuristic to stop slow operations */
    public static boolean hasBeenMarkedAsChangedInMemory() {
        return sMarkedInMemory;
    }
}
