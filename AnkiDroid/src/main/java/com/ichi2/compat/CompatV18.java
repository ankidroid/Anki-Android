package com.ichi2.compat;

import android.annotation.TargetApi;
import android.os.StatFs;

@TargetApi(18)
public class CompatV18 extends CompatV17 implements Compat {

    @Override
    public long getAvailableBytes(StatFs stat) { return stat.getAvailableBytes(); }
    @Override
    public long getTotalBytes(StatFs stat) { return stat.getTotalBytes(); }
}
