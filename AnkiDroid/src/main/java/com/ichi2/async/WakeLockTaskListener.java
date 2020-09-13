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

package com.ichi2.async;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.utils.Permissions;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

import static android.os.PowerManager.*;

@SuppressLint("DirectSystemCurrentTimeMillisUsage")
public abstract class WakeLockTaskListener<TContext extends Context> extends TaskListenerWithContext<TContext> {

    /* 30 minutes */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    @Nullable
    private final PowerManager.WakeLock mWakeLock;
    private final String mWakeLockName;

    private long startTime;


    public WakeLockTaskListener(TContext context, String wakeLockName) {
        super(context);
        this.mWakeLockName = wakeLockName;

        mWakeLock = getWakeLock(context, wakeLockName);
    }


    @Nullable
    private PowerManager.WakeLock getWakeLock(TContext context, String wakeLockName) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm.newWakeLock(PARTIAL_WAKE_LOCK,
                    context.getString(R.string.app_name) + ":" + wakeLockName);
        } catch (Exception e) {
            Timber.w(e, "Failed to obtain WakeLock");
            return null;
        }
    }


    @Override
    @CallSuper
    public void onCancelled() {
        try {
            super.onCancelled();
        } finally {
            releaseLock();
        }
    }

    @Override
    @CallSuper
    protected void onBeforePreExecute() {
        startTime = getCurrentRealTime();
        if (mWakeLock != null && Permissions.canUseWakeLock(AnkiDroidApp.getInstance().getApplicationContext())) {
            acquireLock(mWakeLock);
        }
    }


    private void acquireLock(@NonNull PowerManager.WakeLock wakeLock) {
        try {
            wakeLock.acquire(TIMEOUT_MS);
        } catch (Exception e) {
            AnkiDroidApp.sendExceptionReport(e, "wakeLock " + mWakeLockName);
        }
    }


    @Override
    @CallSuper
    protected void onBeforePostExecute(TaskData result) {
        releaseLock();
    }


    private void releaseLock() {
        if (mWakeLock == null) {
            return;
        }
        if (mWakeLock.isHeld()) {
            try {
                mWakeLock.release();
            } catch (Exception e) {
                AnkiDroidApp.sendExceptionReport(e, "wakeLock " + mWakeLockName, "failed to release lock");
            }
        }
        long elapsed = getCurrentRealTime() - startTime;
        if (elapsed > TIMEOUT_MS) {
            // Log a silent exception
            AnkiDroidApp.sendExceptionReport(new RuntimeException("Operation exceeded wakelock time"), mWakeLockName, Long.toString(elapsed), true);
        }
    }

    private long getCurrentRealTime() {
        return System.currentTimeMillis();
    }
}