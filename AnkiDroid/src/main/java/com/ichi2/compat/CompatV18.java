package com.ichi2.compat;

import android.annotation.TargetApi;
import android.os.StatFs;

import androidx.annotation.CheckResult;

@TargetApi(18)
public class CompatV18 extends CompatV17 implements Compat {

    @Override
    @CheckResult
    public long getAvailableBytes(StatFs stat) { return stat.getAvailableBytes(); }
}
