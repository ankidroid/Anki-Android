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
package com.ichi2.utils

import android.content.Context
import android.content.pm.PackageManager
import com.ichi2.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.compat.PackageInfoFlagsCompat
import timber.log.Timber
import java.lang.Exception
import java.util.*

object CheckCameraPermission {
    fun manifestContainsPermission(context: Context): Boolean {
        try {
            val requestedPermissions = context.getPackageInfoCompat(
                context.packageName,
                PackageInfoFlagsCompat.of(PackageManager.GET_PERMISSIONS.toLong())
            ).requestedPermissions
            if (Arrays.toString(requestedPermissions).contains("android.permission.CAMERA")) {
                return true
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }
}
