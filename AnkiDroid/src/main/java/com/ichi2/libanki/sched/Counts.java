//noinspection MissingCopyrightHeader #8659
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
        mNew = Math.max(new_, 0);
        mLrn = Math.max(lrn, 0);
        mRev = Math.max(rev, 0);
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
                addNew(number);
                break;
            case LRN:
                addLrn(number);
                break;
            case REV:
                addRev(number);
                break;
            default:
                throw new RuntimeException("Index " + index + " does not exists.");
        }
    }

    public void addNew(int new_) {
        mNew = Math.max(mNew + new_, 0);
    }

    public void addLrn(int lrn) {
        mLrn = Math.max(mLrn + lrn, 0);
    }

    public void addRev(int rev) {
        mRev = Math.max(mRev + rev, 0);
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
}
