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
}
