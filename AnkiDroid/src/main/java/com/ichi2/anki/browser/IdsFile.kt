// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.os.Parcel
import android.os.Parcelable
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Temporary file containing cards or note IDs to be passed in a Bundle.
 *
 * It avoids [android.os.TransactionTooLargeException] when passing a big amount of data.
 */
class IdsFile(
    path: String,
) : File(path),
    Parcelable {
    /**
     * @param directory parent directory of the file. Generally it should be the cache directory
     * @param ids ids to store
     */
    constructor(directory: File, ids: List<Long>, prefix: String = "ids") : this(path = createTempFile(prefix, ".tmp", directory).path) {
        DataOutputStream(FileOutputStream(this)).use { outputStream ->
            outputStream.writeInt(ids.size)
            for (id in ids) {
                outputStream.writeLong(id)
            }
        }
    }

    fun getIds(): List<Long> =
        DataInputStream(FileInputStream(this)).use { inputStream ->
            val size = inputStream.readInt()
            List(size) { inputStream.readLong() }
        }

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(path)
    }

    companion object {
        @JvmField
        @Suppress("unused")
        val CREATOR =
            object : Parcelable.Creator<IdsFile> {
                override fun createFromParcel(source: Parcel?): IdsFile = IdsFile(source!!.readString()!!)

                override fun newArray(size: Int): Array<IdsFile> = arrayOf()
            }
    }
}

/** Attempt to delete the associated [IdsFile] and logs the result */
fun IdsFile.removeSafely(owner: String) {
    runCatching { delete() }
        .onFailure { throwable ->
            Timber.w(
                throwable,
                "Exception when removing IdsFile of $owner",
            )
        }.onSuccess { status ->
            Timber.i(
                "$owner associated IdsFile was deleted: $status",
            )
        }
}
