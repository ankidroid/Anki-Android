package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.pm.PackageInfo;

/** Implementation of {@link Compat} for SDK level 28 */
@TargetApi(28)
public class CompatV28 extends CompatV26 implements Compat {

    @Override
    public long getVersionCode(PackageInfo pInfo) {
        return pInfo.getLongVersionCode() ;
    }
}
