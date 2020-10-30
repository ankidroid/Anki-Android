package com.ichi2.libanki.sched;


import java.util.Arrays;

import androidx.annotation.CheckResult;
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

    @CheckResult
    public int getLrn() {
        return mLrn;
    }

    @CheckResult
    public int getNew() {
        return mNew;
    }

    @CheckResult
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
    @CheckResult
    public int count() {
        return mNew + mLrn + mRev;
    }


    @Override
    @CheckResult
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
    @CheckResult
    public int hashCode() {
        return Arrays.asList(mNew, mRev, mLrn).hashCode();
    }
}
