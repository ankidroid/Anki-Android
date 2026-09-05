// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import com.ichi2.utils.Permissions
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject

/** a 'full' build: [MANAGE_EXTERNAL_STORAGE] is declared in the manifest */
fun withManageExternalStorageInManifest(block: () -> Unit) {
    mockkObject(Permissions)
    every { Permissions.canManageExternalStorage(any()) } returns true
    try {
        block()
    } finally {
        unmockkObject(Permissions)
    }
}
