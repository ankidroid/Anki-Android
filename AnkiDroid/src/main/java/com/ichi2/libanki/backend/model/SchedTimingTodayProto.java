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

package com.ichi2.libanki.backend.model;

import BackendProto.AdBackend;

/**
 * Adapter for SchedTimingTodayOut2 result from Rust
 */
public class SchedTimingTodayProto implements SchedTimingToday {

    private final AdBackend.SchedTimingTodayOut2 mData;

    public SchedTimingTodayProto(AdBackend.SchedTimingTodayOut2 data) {
        mData = data;
    }


    @Override
    public int days_elapsed() {
        return mData.getDaysElapsed();
    }


    @Override
    public long next_day_at() {
        return mData.getNextDayAt();
    }
}
