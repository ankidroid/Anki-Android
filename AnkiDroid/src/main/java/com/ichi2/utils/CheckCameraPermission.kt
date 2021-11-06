/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>                                *
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

import timber.log.Timber;

public class CheckCameraPermission {

    public static boolean manifestContainsPermission(Context context) {
        try {
            String[] requestedPermissions = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS)
                    .requestedPermissions;
            if (Arrays.toString(requestedPermissions).contains("android.permission.CAMERA")) {
                return true;
            }
        } catch (Exception e) {
            Timber.w(e);
        }
        return false;
    }
}
