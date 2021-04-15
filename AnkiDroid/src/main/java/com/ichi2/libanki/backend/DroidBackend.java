/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki.backend;

import com.ichi2.libanki.DB;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.backend.exception.BackendNotSupportedException;
import com.ichi2.libanki.backend.model.SchedTimingToday;

import net.ankiweb.rsdroid.RustV1Cleanup;

import androidx.annotation.VisibleForTesting;

/**
 * Interface to the rust backend listing all currently supported functionality.
 */
public interface DroidBackend {
    DB openCollectionDatabase(String path);
    void closeCollection();

    /** Whether a call to {@link DroidBackend#openCollectionDatabase(String)} will generate a schema and indices for the database */
    boolean databaseCreationCreatesSchema();

    boolean isUsingRustBackend();

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    void debugEnsureNoOpenPointers();

    /**
     * Obtains Timing information for the current day.
     *
     * @param createdSecs A UNIX timestamp of the collection creation time
     * @param createdMinsWest The offset west of UTC at the time of creation (eg UTC+10 hours is -600)
     * @param nowSecs timestamp of the current time
     * @param nowMinsWest The current offset west of UTC
     * @param rolloverHour The hour of the day the rollover happens (eg 4 for 4am)
     * @return Timing information for the current day. See {@link SchedTimingToday}.
     */
    SchedTimingToday sched_timing_today(long createdSecs, int createdMinsWest, long nowSecs, int nowMinsWest, int rolloverHour) throws BackendNotSupportedException;

    /**
     * For the given timestamp, return minutes west of UTC in the local timezone.<br/><br/>
     *
     * eg, Australia at +10 hours is -600.<br/>
     * Includes the daylight savings offset if applicable.
     *
     * @param timestampSeconds The timestamp in seconds
     * @return minutes west of UTC in the local timezone
     */
    int local_minutes_west(long timestampSeconds) throws BackendNotSupportedException;

    @RustV1Cleanup("backend.newDeckConfigLegacy")
    default DeckConfig new_deck_config_legacy() {
        return new DeckConfig(Decks.DEFAULT_CONF);
    }
}
