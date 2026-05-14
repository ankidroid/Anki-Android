/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.ui.windows.permissions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.addCallback
import androidx.core.content.IntentCompat
import androidx.fragment.app.commit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.databinding.ActivityPermissionsBinding
import com.ichi2.anki.storage.StorageDecision
import com.ichi2.themes.Themes
import com.ichi2.themes.setTransparentStatusBar
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Screen responsible for getting permissions from the user.
 *
 * Prefer using [PermissionsActivity.getIntent] to get an intent to this activity.
 *
 * Advantages:
 * * Explains why each permission should be granted
 * * Easily reusable
 * * Doesn't need to block any UI elements or background routines that depends on a permission.
 *     Nor needs to add callbacks after the permissions are granted
 *
 * To request optional permissions from the user, prefer [PermissionsBottomSheet].
 */
class PermissionsActivity : AnkiActivity(R.layout.activity_permissions) {
    private val binding by viewBinding(ActivityPermissionsBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setViewBinding(binding)
        Themes.setTheme(this)
        setTransparentStatusBar()

        binding.continueButton.setOnClickListener {
            if (CollectionHelper.storageDecision() == StorageDecision.Decided) {
                finish()
            } else {
                showPrivateStorageWarningDialog()
            }
        }
        // #20881: Activity should not be launchd without extras
        val permissionSet = IntentCompat.getParcelableExtra(intent, PERMISSIONS_SET_EXTRA, PermissionSet::class.java)
        if (permissionSet == null) {
            Timber.w("PERMISSIONS_SET_EXTRA not set; finishing")
            showThemedToast(this, R.string.something_wrong, false)
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val permissionsFragment =
            requireNotNull(permissionSet.permissionsFragment?.getDeclaredConstructor()?.newInstance()) {
                "invalid permissionsFragment"
            }

        supportFragmentManager.commit {
            replace(R.id.fragment_container, permissionsFragment)
        }
        // only close the activity by tapping the continue button
        onBackPressedDispatcher.addCallback {}
    }

    // TODO Rethink the UI - selection UI is preferred over a warning dialog: #21049
    private fun showPrivateStorageWarningDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.private_storage_warning_title)
            .setMessage(R.string.private_storage_warning)
            .setPositiveButton(R.string.dialog_continue) { _, _ ->
                if (!CollectionHelper.setPrivateStoragePath(this)) {
                    showThemedToast(this, R.string.something_wrong, false)
                    return@setPositiveButton
                }
                finish()
            }.setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    companion object {
        const val PERMISSIONS_SET_EXTRA = "permissionsSet"

        fun getIntent(
            context: Context,
            permissionsSet: PermissionSet,
        ): Intent =
            Intent(context, PermissionsActivity::class.java).apply {
                putExtra(PERMISSIONS_SET_EXTRA, permissionsSet as Parcelable)
            }
    }
}
