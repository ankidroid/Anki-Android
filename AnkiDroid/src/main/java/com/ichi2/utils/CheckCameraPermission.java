/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Arrays;

public class CheckCameraPermission {
    private final Context mContext;

    public CheckCameraPermission(Context mContext) {
        this.mContext = mContext;
    }

    public boolean checkManifestCameraPermission() {
        try {
            String[] requestedPermissions = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            if (Arrays.toString(requestedPermissions).contains("android.permission.CAMERA")) {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return true;
    }
}
