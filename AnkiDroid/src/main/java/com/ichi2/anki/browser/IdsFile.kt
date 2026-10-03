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
import java.io.IOException

/**
 * Temporary file containing cards or note IDs to be passed in a Bundle.
 *
 * It avoids [android.os.TransactionTooLargeException] when passing a big amount of data.
 *
 * For activity saved-state files, use [com.ichi2.anki.common.utils.ext.onPermanentDismissal] to
 * clear state.
 *
 * Do not use `ViewModel.onCleared()`; see `onPermanentDismissal`.
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

    /**
     * Reads the Ids in the file. This may be an empty list.
     *
     * @throws IOException if the file is missing or corrupt.
     */
    fun getIds(): List<Long> =
        FileInputStream(this).use { fileStream ->
            val inputStream = DataInputStream(fileStream.buffered())
            val size = inputStream.readInt()
            // Check before allocating: a corrupt count must not cause an enormous allocation,
            // and a partial selection must never be used to perform an operation.
            if (size < 0 || fileStream.channel.size() != Int.SIZE_BYTES + size.toLong() * Long.SIZE_BYTES) {
                throw IOException("Invalid IDs file length or count: $name")
            }
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
