// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.utils.FileUtil
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.days

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
     * @param directory parent directory of the file. Generally it should be [getDirectory]
     * @param ids ids to store
     */
    constructor(directory: File, ids: List<Long>, purpose: Purpose) : this(
        path = createTempFile(purpose.prefix, ".tmp", ensureDirectory(directory)).path,
    ) {
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

    enum class Purpose(
        /** Uniquely identifies snapshots by filename: `multiselect-values123456789.tmp` */
        val prefix: String,
    ) {
        SELECTION(prefix = "multiselect-values"),
        FIND_AND_REPLACE(prefix = "find-replace"),
        TAGS(prefix = "ids"),
        PREVIEW(prefix = "ids"),
        EXPORT(prefix = "export"),
        SET_DUE_DATE(prefix = "set-due-date"),
    }

    companion object {
        private const val DIRECTORY_NAME = "idsfiles"

        /** Maximum age of a saved selection, including files still referenced by saved state. */
        private val FILE_RETENTION = 7.days

        /**
         * Deletes snapshots older than [FILE_RETENTION], mostly files abandoned after process death.
         *
         * Called at startup on an IO dispatcher.
         *
         * Note: There is a slim chance that this will clear active snapshots, so downstream code
         * must handle this possibility.
         */
        fun deleteStaleFiles(context: Context = appContext) {
            // Do not create the snapshot directory just to clean it.
            deleteStaleFiles(File(context.cacheDir, DIRECTORY_NAME))
        }

        @VisibleForTesting
        internal fun deleteStaleFiles(directory: File) {
            val cutoff = TimeManager.time.intTimeMS() - FILE_RETENTION.inWholeMilliseconds
            val deleteCount =
                FileUtil.listFilesOrEmpty(directory).count { file ->
                    file.withCleanupErrorHandling {
                        file.isStale(cutoff) && file.deleteWithLogging()
                    }
                }
            Timber.i("Deleted %d expired ID snapshot(s)", deleteCount)
        }

        private fun File.isStale(cutoff: Long): Boolean {
            val modified = lastModified()
            return isFile && modified > 0 && modified < cutoff
        }

        private fun File.deleteWithLogging(): Boolean =
            delete().also { deleted ->
                if (!deleted) Timber.w("Unable to delete expired ID snapshot: %s", this)
            }

        private inline fun File.withCleanupErrorHandling(block: () -> Boolean): Boolean =
            try {
                block()
            } catch (e: SecurityException) {
                Timber.w(e, "Unable to access ID snapshot path: %s", this)
                false
            }

        /**
         * Returns the cache path for saved selections. The directory is created when writing a snapshot.
         * Android may evict these files at any age; callers must continue handling missing snapshots.
         */
        fun getDirectory(context: Context = appContext): File = File(context.cacheDir, DIRECTORY_NAME)

        private fun ensureDirectory(directory: File): File {
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Unable to create ID snapshot directory: $directory")
            }
            return directory
        }

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
