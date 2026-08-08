// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Tests for [selectStoragePermissions]
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class) // no point in Application init if we don't use it
class SelectStoragePermissionsTest {
    @Config(sdk = [BEFORE_Q])
    @Test
    fun startupBeforeQ() {
        val expectedPermissions =
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.INTERNET,
            )

        // force a safe startup before Q
        assertThat(
            selectStoragePermissions(canManageExternalStorage = false).permissions.asIterable(),
            contains(*expectedPermissions),
        )
        assertThat(
            selectStoragePermissions(canManageExternalStorage = true).permissions.asIterable(),
            contains(*expectedPermissions),
        )
    }

    @Config(sdk = [Q])
    @Test
    fun startupQ() {
        assertThat(selectStoragePermissions(canManageExternalStorage = false), equalTo(PermissionSet.LEGACY_ACCESS))
        assertThat(selectStoragePermissions(canManageExternalStorage = true), equalTo(PermissionSet.LEGACY_ACCESS))
    }

    @SuppressLint("InlinedApi")
    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `Android 11 - After upgrade from AnkiDroid 2 15 (with MANAGE_EXTERNAL_STORAGE)`() {
        // after an upgrade, all we need is READ/WRITE. Once we reinstall, we need MANAGE_EXTERNAL_STORAGE
        val expectedPermissions =
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.INTERNET,
            )

        selectStoragePermissions(
            canManageExternalStorage = true,
            currentFolderIsAccessibleAndLegacy = true,
        ).let {
            assertThat(
                it.permissions.asIterable(),
                contains(*expectedPermissions),
            )
        }
    }

    @SuppressLint("InlinedApi")
    @Config(sdk = [R_OR_AFTER])
    @Test
    fun `Android 11 - After reinstall (with MANAGE_EXTERNAL_STORAGE)`() {
        val permissions =
            selectStoragePermissions(
                canManageExternalStorage = true,
                currentFolderIsAccessibleAndLegacy = false,
            )

        assertTrue(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE in permissions.permissions)
    }

    @Config(sdk = [R_OR_AFTER])
    @Test
    fun startupAfterQWithoutManageExternalStorage() {
        assertThat(
            selectStoragePermissions(canManageExternalStorage = false),
            equalTo(PermissionSet.APP_PRIVATE),
        )
    }

    /**
     * Helper for [com.ichi2.anki.selectStoragePermissions], making `currentFolderIsAccessibleAndLegacy` optional
     */
    private fun selectStoragePermissions(
        canManageExternalStorage: Boolean,
        currentFolderIsAccessibleAndLegacy: Boolean = false,
    ): PermissionSet =
        com.ichi2.anki.selectStoragePermissions(
            canManageExternalStorage = canManageExternalStorage,
            currentFolderIsAccessibleAndLegacy = currentFolderIsAccessibleAndLegacy,
        )

    companion object {
        const val BEFORE_Q = Build.VERSION_CODES.Q - 1
        const val Q = Build.VERSION_CODES.Q
        const val R_OR_AFTER = Build.VERSION_CODES.R
    }
}
