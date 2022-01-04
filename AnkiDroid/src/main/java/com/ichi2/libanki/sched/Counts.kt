/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.libanki.sched;


import java.util.Arrays;

import androidx.annotation.NonNull;

/**
 * Represents the three counts shown in deck picker and reviewer. Semantically more meaningful than int[]
 */
public class Counts {
    public enum Queue {
        NEW, LRN, REV
    }

    private int mNew;
    private int mLrn;
    private int mRev;

    public Counts() {
        this(0, 0, 0);
    }

    public Counts(int new_, int lrn, int rev) {
        mNew = new_;
        mLrn = lrn;
        mRev = rev;
    }

    public int getLrn() {
        return mLrn;
    }

    public int getNew() {
        return mNew;
    }

    public int getRev() {
        return mRev;
    }

    /**
     * @param index Queue in which it elements are added
     * @param number How much to add. */
    public void changeCount(@NonNull Queue index, int number) {
        switch (index) {
            case NEW:
                mNew += number;
                break;
            case LRN:
                mLrn += number;
                break;
            case REV:
                mRev += number;
                break;
            default:
                throw new RuntimeException("Index " + index + " does not exists.");
        }
    }

    public void addNew(int new_) {
        mNew += new_;
    }

    public void addLrn(int lrn) {
        mLrn += lrn;
    }

    public void addRev(int rev) {
        mRev += rev;
    }


    /**
     * @return the sum of the three counts
     */
    public int count() {
        return mNew + mLrn + mRev;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Counts counts = (Counts) o;
        return mNew == counts.mNew &&
                mRev == counts.mRev &&
                mLrn == counts.mLrn;
    }


    @Override
    public int hashCode() {
        return Arrays.asList(mNew, mRev, mLrn).hashCode();
    }


    @NonNull
    @Override
    public String toString() {
        return "[" + mNew + ", " + mLrn + ", " + mRev + "]";
    }
}
