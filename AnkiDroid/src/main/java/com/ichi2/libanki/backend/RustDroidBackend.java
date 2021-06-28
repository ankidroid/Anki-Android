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

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.backend.exception.BackendNotSupportedException;
import com.ichi2.libanki.backend.model.SchedTimingToday;
import com.ichi2.libanki.backend.model.SchedTimingTodayProto;

import net.ankiweb.rsdroid.BackendFactory;

import BackendProto.AdBackend;

/** The Backend in Rust */
public class RustDroidBackend implements DroidBackend {
    public static final int UNUSED_VALUE = 0;

    // I think we can change this to BackendV1 once new DB() accepts it.
    private final BackendFactory mBackend;


    public RustDroidBackend(BackendFactory mBackend) {
        this.mBackend = mBackend;
    }


    @Override
    public DB openCollectionDatabase(String path) {
        return new DB(path, mBackend);
    }


    @Override
    public void closeCollection() {
        mBackend.closeCollection();
    }


    @Override
    public boolean databaseCreationCreatesSchema() {
        return true;
    }


    @Override
    public boolean isUsingRustBackend() {
        return true;
    }


    @Override
    public void debugEnsureNoOpenPointers() {
        AdBackend.DebugActiveDatabaseSequenceNumbersOut result = mBackend.getBackend().debugActiveDatabaseSequenceNumbers(UNUSED_VALUE);
        if (result.getSequenceNumbersCount() > 0) {
            String numbers = result.getSequenceNumbersList().toString();
            throw new IllegalStateException("Contained unclosed sequence numbers: " + numbers);
        }
    }

    @Override
    public SchedTimingToday sched_timing_today(long createdSecs, int createdMinsWest, long nowSecs, int nowMinsWest, int rolloverHour) {
        AdBackend.SchedTimingTodayOut2 res = mBackend.getBackend().schedTimingTodayLegacy(createdSecs, createdMinsWest, nowSecs, nowMinsWest, rolloverHour);
        return new SchedTimingTodayProto(res);
    }


    @Override
    public int local_minutes_west(long timestampSeconds) {
        return mBackend.getBackend().localMinutesWest(timestampSeconds).getVal();
    }


    @Override
    public void useNewTimezoneCode(Collection col) {
        // enable the new timezone code on a new collection
        try {
            col.getSched().set_creation_offset();
        } catch (BackendNotSupportedException e) {
            throw e.alreadyUsingRustBackend();
        }
    }
}
