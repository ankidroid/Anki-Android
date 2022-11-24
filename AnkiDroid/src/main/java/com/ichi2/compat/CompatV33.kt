/*
 *  Copyright (c) 2022 Ashish Yadav <mailtoashish693@gmail.com>
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.compat

import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import java.io.Serializable

@TargetApi(33)
open class CompatV33 : CompatV31(), Compat {
    override fun <T : Parcelable> getParcelableArrayList(bundle: Bundle, key: String, clazz: Class<T>): ArrayList<T>? {
        return bundle.getParcelableArrayList(key, clazz)
    }

    override fun <T : Serializable?> getSerializableExtra(intent: Intent, name: String, className: Class<T>): T? {
        return intent.getSerializableExtra(name, className)
    }

    override fun <T : Parcelable?> getParcelableExtra(intent: Intent, name: String, clazz: Class<T>): T? {
        return intent.getParcelableExtra(name, clazz)
    }

    override fun <T : Serializable?> getSerializable(bundle: Bundle, key: String, clazz: Class<T>): T? {
        return bundle.getSerializable(key, clazz)
    }

    override fun <T> readSparseArray(
        parcel: Parcel,
        loader: ClassLoader,
        clazz: Class<T>
    ): SparseArray<T>? {
        return parcel.readSparseArray(loader, clazz)
    }

    override fun getPackageInfo(packageManager: PackageManager, packageName: String, flags: PackageInfoFlagsCompat): PackageInfo? =
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.value))
}
