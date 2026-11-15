// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.annotation.SuppressLint
import android.os.Environment
import com.ichi2.anki.common.permissions.isExternalStorageManager
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import java.io.File

/** The default collection path in public storage: `~/AnkiDroid` */
val publicCollectionPath: String
    get() = File(Environment.getExternalStorageDirectory(), "AnkiDroid").absolutePath

fun withUndeletableContents(
    folder: File,
    block: () -> Unit,
) {
    File(folder, "locked.txt").apply {
        parentFile?.mkdirs()
        createNewFile()
    }
    assumeTrue("The filesystem must support making directories unwritable", folder.setWritable(false, false))
    try {
        assumeFalse("A privileged test runner can still delete from an unwritable folder", folder.canWrite())
        block()
    } finally {
        folder.setWritable(true, true)
    }
}

/** 'All files access' (`MANAGE_EXTERNAL_STORAGE`) is granted */
@SuppressLint("NewApi") // isExternalStorageManager requires R: callers run at API 30 or above
fun withAllFilesAccess(block: () -> Unit) {
    mockkStatic(::isExternalStorageManager)
    every { isExternalStorageManager() } returns true
    try {
        block()
    } finally {
        unmockkStatic(::isExternalStorageManager)
    }
}
