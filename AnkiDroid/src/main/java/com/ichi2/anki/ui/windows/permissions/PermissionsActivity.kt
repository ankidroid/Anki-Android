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
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import com.ichi2.themes.setTransparentStatusBar

/**
 * Screen responsible for getting permissions from the user.
 * Also the activity that shows up when the user selects a more-info button in the OS permission settings.
 * See https://developer.android.com/training/permissions/explaining-access#privacy-dashboard
 *
 * Prefer using [PermissionsActivity.getIntent] to get an intent to this activity.
 *
 * Advantages:
 * * Explains why each permission should be granted
 * * Easily reusable
 * * Doesn't need to block any UI elements or background routines that depends on a permission.
 *     Nor needs to add callbacks after the permissions are granted
 * * TODO Show which permissions are mandatory and which are optional
 */
class PermissionsActivity : AnkiActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        Themes.setTheme(this)
        setContentView(R.layout.permissions_activity)
        setTransparentStatusBar()

        when (intent.action) {
            Intent.ACTION_VIEW_PERMISSION_USAGE, Intent.ACTION_VIEW_PERMISSION_USAGE_FOR_PERIOD -> {
                // These actions can trigger this activity due to an intent filter in the manifest
                // if the API version is at least 31. We double check the API version to pass the lint check.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    showAllPermissionsExplanation()
                }
            }
            // Otherwise, for a normal call from getIntent:
            else -> requestPermissionsInPermissionSet()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun showAllPermissionsExplanation() {
        findViewById<AppCompatButton>(R.id.continue_button).isVisible = false
        supportFragmentManager.commit {
            replace(R.id.fragment_container, AllPermissionsExplanation())
        }
    }

    private fun requestPermissionsInPermissionSet() {
        findViewById<AppCompatButton>(R.id.continue_button).setOnClickListener {
            finish()
        }

        val permissionSet =
            requireNotNull(IntentCompat.getParcelableExtra(intent, PERMISSIONS_SET_EXTRA, PermissionSet::class.java)) {
                "PERMISSIONS_SET_EXTRA not set"
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

    fun setContinueButtonEnabled(isEnabled: Boolean) {
        findViewById<AppCompatButton>(R.id.continue_button).isEnabled = isEnabled
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
