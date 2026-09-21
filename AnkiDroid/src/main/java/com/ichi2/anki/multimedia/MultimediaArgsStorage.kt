// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.content.Context
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Stores note contents outside intents and saved state, which have a shared Binder size limit. */
internal class MultimediaArgsStorage private constructor(
    private val directory: File,
) {
    fun save(arguments: MultimediaActivityExtra): File =
        File.createTempFile(ARGS_FILE_PREFIX, ".tmp", directory).also { file ->
            try {
                ObjectOutputStream(file.outputStream().buffered()).use { it.writeObject(arguments) }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }

    fun read(file: File): MultimediaActivityExtra =
        ObjectInputStream(file.inputStream().buffered()).use { it.readObject() as MultimediaActivityExtra }

    companion object {
        private const val ARGS_FILE_PREFIX = "multimedia-args"

        fun create(context: Context): MultimediaArgsStorage = MultimediaArgsStorage(context.cacheDir)
    }
}
