/****************************************************************************************
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.utils;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import timber.log.Timber;

/**
 * Helper class for checking for programming errors while using threads.
 */
public class Threads {

    private Threads() {
    }

    /**
     * An object used to check a thread-access policy.
     * <p>
     * It will verify that calls to its {@link #checkThread()} method are done on the right thread.
     */
    public interface ThreadChecker {

        /**
         * Checks that it is called from the right thread and fails otherwise.
         */
        void checkThread();
    }


    /**
     * Creates a {@link ThreadChecker} that validates all access are done on the given thread.
     * 
     * @param thread on which accesses should occur
     */
    public static ThreadChecker newSingleThreadChecker(@NonNull Thread thread) {
        if (thread == null) {
            throw new IllegalArgumentException("thread should not be null");
        }
        return new SingleThreadChecker(thread);
    }


    /**
     * Creates a {@link ThreadChecker} that validates all access are done on the calling thread.
     */
    public static ThreadChecker newCurrentThreadChecker() {
        return new SingleThreadChecker(Thread.currentThread());
    }


    /**
     * Creates a {@link ThreadChecker} that validates all access on the same thread, without specifying which thread.
     * <p>
     * The thread will be determined by the first call to {@link ThreadChecker#checkThread()} and enforced thereafter.
     */
    public static ThreadChecker newLazySingleThreadChecker() {
        return new SingleThreadChecker(null);
    }


    /**
     * @return true if called from the application main thread
     */
    public static boolean isOnMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }


    /**
     * Checks that it is called from the main thread and fails if it is called from another thread.
     */
    @UiThread
    public static void checkMainThread() {
        if (!isOnMainThread()) {
            Timber.e("must be called on the main thread instead of %s", Thread.currentThread());
        }
    }


    /**
     * Checks that it is not called from the main thread and fails if it is.
     */
    @WorkerThread
    public static void checkNotMainThread() {
        if (isOnMainThread()) {
            Timber.e("must not be called on the main thread");
        }
    }

    /**
     * Helper class to track access from a single thread.
     * <p>
     * This class can be used to validate a single-threaded access policy to a class.
     * <p>
     * Each method that needs to be called from a single thread can simply call {@link #checkThread()} to validate the
     * thread it is being called.
     */
    private static class SingleThreadChecker implements ThreadChecker {

        /** The thread that is allowed access. */
        private Thread mThread;


        /**
         * Creates a checker for the given thread.
         * <p>
         * If passed {@code null}, it will detect the first thread that calls {@link #checkThread()} and make sure all
         * future accesses are from that thread.
         * 
         * @param thread that is allowed access
         */
        private SingleThreadChecker(Thread thread) {
            mThread = thread;
        }


        @Override
        public void checkThread() {
            // If this the first access and we have not specified a thread, record the current thread.
            if (mThread == null) {
                mThread = Thread.currentThread();
                return;
            }
            if (mThread != Thread.currentThread()) {
                throw new IllegalStateException("must be called from single thread: " + mThread + " instead of "
                        + Thread.currentThread());
            }
        }

    }

}
