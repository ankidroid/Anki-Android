/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.content.Context;
import android.os.AsyncTask;

import com.ichi2.anki.exception.OutOfSpaceException;

import net.ankiweb.rsdroid.BackendException;
import net.ankiweb.rsdroid.BackendFactory;
import net.ankiweb.rsdroid.RustBackendFailedException;

import java.lang.ref.WeakReference;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import timber.log.Timber;

/** Utilities for launching the first activity (currently the DeckPicker) */
public class InitialActivity {

    private InitialActivity() {

    }

    @NonNull
    @CheckResult
    public static StartupFailure getStartupFailureType(Context context) {
        if (!AnkiDroidApp.isSdCardMounted()) {
            return StartupFailure.SD_CARD_NOT_MOUNTED;
        } else if (!CollectionHelper.isCurrentAnkiDroidDirAccessible(context)) {
            return StartupFailure.DIRECTORY_NOT_ACCESSIBLE;
        }

        CollectionHelper.DatabaseVersion databaseVersion = isFutureAnkiDroidVersion(context);
        switch (databaseVersion) {
            case FUTURE_NOT_DOWNGRADABLE:
                return StartupFailure.FUTURE_ANKIDROID_VERSION;
            case FUTURE_DOWNGRADABLE:
                return StartupFailure.DATABASE_DOWNGRADE_REQUIRED;
            case UNKNOWN:
            case USABLE:
            default:
                try {
                    CollectionHelper.getInstance().getCol(context);
                    return StartupFailure.DB_ERROR;
                } catch (BackendException.BackendDbException.BackendDbLockedException e) {
                    return StartupFailure.DATABASE_LOCKED;
                } catch (Exception ignored) {
                    return StartupFailure.DB_ERROR;
                }
        }
    }

    private static CollectionHelper.DatabaseVersion isFutureAnkiDroidVersion(Context context) {
        try {
            return CollectionHelper.isFutureAnkiDroidVersion(context);
        } catch (Exception e) {
            Timber.w(e, "Could not determine if future AnkiDroid version - assuming not");
            return CollectionHelper.DatabaseVersion.UNKNOWN;
        }
    }


    /**
     * Downgrades the database at the currently selected collection path from V16 to V11 in a background task
     */
    public static void downgradeBackend(DeckPicker deckPicker) {
        // Note: This method does not require a backend pointer or an open collection
        Timber.i("Downgrading backend");
        new PerformDowngradeTask(new WeakReference<>(deckPicker)).execute();
    }


    protected static void downgradeCollection(DeckPicker deckPicker, BackupManager backupManager) throws OutOfSpaceException, RustBackendFailedException {
        if (deckPicker == null) {
            throw new IllegalArgumentException("deckPicker was null");
        }

        String collectionPath = CollectionHelper.getCollectionPath(deckPicker);

        if (!backupManager.performDowngradeBackupInForeground(collectionPath)) {
            throw new IllegalArgumentException("backup failed");
        }

        Timber.d("Downgrading database to V11: '%s'", collectionPath);
        BackendFactory.createInstance().getBackend().downgradeBackend(collectionPath);
    }


    // I disapprove, but it's best to keep consistency with the rest of the app
    private static class PerformDowngradeTask extends AsyncTask<Void, Void, Void> {

        private final WeakReference<DeckPicker> mDeckPicker;
        private Exception mException;


        public PerformDowngradeTask(WeakReference<DeckPicker> deckPicker) {
            mDeckPicker = deckPicker;
        }

        @Override
        protected Void doInBackground(Void... params) {
            // It would be great if we could catch the OutOfSpaceException here
            try {
                DeckPicker deckPicker = mDeckPicker.get();
                downgradeCollection(deckPicker, deckPicker.getBackupManager());
            } catch (Exception e) {
                Timber.w(e);
                this.mException = e;
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            DeckPicker d = mDeckPicker.get();
            if (d != null) {
                d.showProgressBar();
            }
        }



        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            DeckPicker d = mDeckPicker.get();
            if (d == null) {
                return;
            }

            d.hideProgressBar();
            if (mException != null) {
                if (mException instanceof OutOfSpaceException) {
                    d.displayDowngradeFailedNoSpace();
                } else {
                    d.displayDatabaseFailure();
                }
                return;
            }

            Timber.i("Database downgrade successful - starting up");
            // no exception - continue
            d.handleStartup();
            // This call should probably be in handleStartup - but it's also called there onRefresh
            // TODO: PERF: to fix the above, add test to ensure that this is only called once on each startup path
            d.refreshState();
        }
    }


    public enum StartupFailure {
        SD_CARD_NOT_MOUNTED,
        DIRECTORY_NOT_ACCESSIBLE,
        FUTURE_ANKIDROID_VERSION,
        /** A downgrade of the AnkiDroid database is required (and possible) */
        DATABASE_DOWNGRADE_REQUIRED,
        DB_ERROR,
        DATABASE_LOCKED,
    }
}
