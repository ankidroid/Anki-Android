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

public class MockTime extends Time {

    /** Number of miliseconds between each call. */
    private final int mStep;
    /** Time since epoch in MS. */
    private long mTime;

    /** A clock at time Time, only changed explicitly*/
    public MockTime(long time) {
        this(time, 0);
    }

    /** A clock at time Time, each call advance by step ms.*/
    public MockTime(long time, int step) {
        this.mTime = time;
        this.mStep = step;
    }

    /** Time in milisecond since epoch. */
    @Override
    public long intTimeMS() {
        long mTime = this.mTime;
        this.mTime += mStep;
        return mTime;
    }

    protected long getTime() {
        return mTime;
    }

    /** Add ms milisecond*/
    public void addMs(long ms) {
        mTime += ms;
    }

    /** add s seconds */
    public void addS(long s) {
        addMs(s * 1000L);
    }

    /** add m minutes */
    public void addM(long m) {
        addS(m * 60);
    }

    /** add h hours*/
    public void addH(long h) {
        addM(h * 60);
    }

    /** add d days*/
    public void addD(long d) {
        addH(d * 24);
    }
}
