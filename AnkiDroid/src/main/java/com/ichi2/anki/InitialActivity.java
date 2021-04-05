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

import net.ankiweb.rsdroid.BackendException;

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
        } else if (isFutureAnkiDroidVersion(context)) {
            return StartupFailure.FUTURE_ANKIDROID_VERSION;
        } else {
            try {
                CollectionHelper.getInstance().getCol(context);
            } catch (BackendException.BackendDbException.BackendDbLockedException e) {
                return StartupFailure.DATABASE_LOCKED;
            } catch (Exception ignored) {

            }
            return StartupFailure.DB_ERROR;
        }
    }

    private static boolean isFutureAnkiDroidVersion(Context context) {
        try {
            return CollectionHelper.isFutureAnkiDroidVersion(context);
        } catch (Exception e) {
            Timber.w(e, "Could not determine if future AnkiDroid version - assuming not");
            return false;
        }
    }

    public enum StartupFailure {
        SD_CARD_NOT_MOUNTED,
        DIRECTORY_NOT_ACCESSIBLE,
        FUTURE_ANKIDROID_VERSION,
        DB_ERROR,
        DATABASE_LOCKED,
    }
}
