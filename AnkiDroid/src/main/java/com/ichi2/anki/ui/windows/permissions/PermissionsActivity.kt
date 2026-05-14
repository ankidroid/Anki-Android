// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.R
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.isLegacyStorage
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.getParcelableExtraCompat
import com.ichi2.anki.databinding.ActivityPermissionsBinding
import com.ichi2.anki.ui.windows.permissions.PermissionsFragment.Companion.REQUEST_PERMISSIONS
import com.ichi2.anki.utils.ext.setFragmentResultListener
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
    internal val binding by viewBinding(ActivityPermissionsBinding::bind)
    private lateinit var requestedPermissions: StoragePermissionSet
    private lateinit var permissionSet: StoragePermissionSet
    private val storagePathListener =
        OnSharedPreferenceChangeListener { _, key ->
            if (key == CollectionHelper.PREF_COLLECTION_PATH && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !isFinishing) {
                refreshPermissions()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setViewBinding(binding)

        // #20881: Activity should not be launchd without extras
        val requested = intent.getParcelableExtraCompat<StoragePermissionSet>(EXTRA_PERMISSIONS_SET)
        if (requested == null) {
            Timber.w("EXTRA_PERMISSIONS_SET not set; finishing")
            showThemedToast(this, CommonString.something_wrong, false)
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        requestedPermissions = requested
        // Android may restore the original public-storage request after the private choice was saved.
        permissionSet = currentPermissions()
        if (permissionSet != requestedPermissions && permissionSet.hasRequiredPermissions(this)) {
            finish()
            return
        }
        configurePermissions()
        // Back must not bypass the required permissions.
        onBackPressedDispatcher.addCallback {}
    }

    override fun onResume() {
        super.onResume()
        if (!::permissionSet.isInitialized || isFinishing) return
        sharedPrefs().registerOnSharedPreferenceChangeListener(storagePathListener)
        // Catch changes made while stopped, when preference notifications were not observed.
        refreshPermissions()
    }

    override fun onPause() {
        sharedPrefs().unregisterOnSharedPreferenceChangeListener(storagePathListener)
        super.onPause()
    }

    private fun configurePermissions() {
        setFragmentResultListener(REQUEST_PERMISSIONS) { _, _ -> refreshPermissions() }
        setFragmentResultListener(REQUEST_PERMISSIONS_GRANTED) { _, _ ->
            refreshPermissions()
            if (permissionSet.hasRequiredPermissions(this)) decideStorageAndFinish()
        }

        showPermissionsFragment()
        binding.continueButton.setOnClickListener {
            refreshPermissions()
            if (permissionSet.hasRequiredPermissions(this)) {
                decideStorageAndFinish()
                return@setOnClickListener
            }
        }
        refreshPermissions()
    }

    private fun showPermissionsFragment() {
        val permissionsFragment = permissionSet.permissionsFragment.getDeclaredConstructor().newInstance()
        supportFragmentManager.commit {
            replace(R.id.fragment_container, permissionsFragment)
        }
    }

    private fun currentPermissions(): StoragePermissionSet =
        if (isLegacyStorage(this, setCollectionPath = false) == false) StoragePermissionSet.APP_PRIVATE else requestedPermissions

    private fun refreshPermissions() {
        // Another task may have changed the collection path.
        val currentPermissions = currentPermissions()
        if (permissionSet != currentPermissions) {
            permissionSet = currentPermissions
            showPermissionsFragment()
        }
        binding.continueButton.isEnabled = permissionSet.hasRequiredPermissions(this)
    }

    /**
     * Records the storage decision and closes the screen: the granted permissions
     * determine the default collection path.
     *
     * Called on 'Continue', or when the fragment reports [REQUEST_PERMISSIONS_GRANTED].
     * Preserves an existing collection path.
     *
     * @see InitialActivity.decideStorageIfUndecided
     */
    private fun decideStorageAndFinish() {
        InitialActivity.decideStorageIfUndecided(this)
        finish()
    }

    companion object {
        const val EXTRA_PERMISSIONS_SET = "permissionsSet"

        /**
         * Fragment result request key: all the fragment's permissions are granted.
         *
         * Signals the activity to persist the
         * [storage decision][com.ichi2.anki.common.storage.StorageDecision], and then close.
         */
        const val REQUEST_PERMISSIONS_GRANTED = "result_complete"

        fun getIntent(
            context: Context,
            permissionsSet: StoragePermissionSet,
        ): Intent =
            Intent(context, PermissionsActivity::class.java).apply {
                putExtra(EXTRA_PERMISSIONS_SET, permissionsSet as Parcelable)
            }
    }
}
