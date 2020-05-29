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

package com.ichi2.testutils;

import com.ichi2.libanki.utils.Time;

import java.util.Date;

public class MockTime implements Time {

    private final long mTime;

    public MockTime(long time) {
        mTime = time;
    }

    @Override
    public long time() {
        return mTime;
    }


    @Override
    public Date getCurrentDate() {
        return new Date(mTime);
    }

    /**These need confirmation */

    @Override
    public long intTime() {
        return (long) now();
    }


    @Override
    public double now() {
        return (double)mTime / 1000.0d;
    }
}
