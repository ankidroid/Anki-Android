// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.withStarted
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.isLegacyStorage
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.getParcelableExtraCompat
import com.ichi2.anki.databinding.ActivityPermissionsBinding
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.ui.windows.permissions.PermissionsFragment.Companion.REQUEST_PERMISSIONS
import com.ichi2.anki.ui.windows.permissions.PermissionsViewModel.StorageCompletion
import com.ichi2.anki.utils.ext.setFragmentResultListener
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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
    private val viewModel: PermissionsViewModel by viewModels { PermissionsViewModel.factory(application) }
    private lateinit var requestedPermissions: StoragePermissionSet
    private lateinit var permissionSet: StoragePermissionSet
    private val completingStorage: Boolean
        get() = viewModel.storageCompletion != null
    private var completionObserver: Job? = null
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
        permissionSet = if (viewModel.choosingAppFolder) requestedPermissions else currentPermissions()
        if (permissionSet != requestedPermissions && !completingStorage && permissionSet.hasRequiredPermissions(this)) {
            finish()
            return
        }
        configurePermissions()
        // Back must not bypass the required permissions.
        onBackPressedDispatcher.addCallback {}
        viewModel.storageCompletion?.let(::observeStorageCompletion)
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
            // Accept a later public choice even while app-folder creation is still running.
            if (permissionSet.hasRequiredPermissions(this)) completeStorageChoice()
        }

        showPermissionsFragment()
        binding.continueButton.setOnClickListener {
            if (completingStorage) return@setOnClickListener
            refreshPermissions()
            completeStorageChoice()
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
        if (isFinishing) return
        // A previous task's worker can save the private path after its ViewModel has been cleared.
        // Our own setup keeps its choice visible until the completion handler consumes the result.
        val currentPermissions = if (viewModel.choosingAppFolder) requestedPermissions else currentPermissions()
        if (permissionSet != currentPermissions) {
            permissionSet = currentPermissions
            showPermissionsFragment()
        }
        val hasRequiredPermissions = permissionSet.hasRequiredPermissions(this)
        val canUseAppFolder = !hasRequiredPermissions && viewModel.canChooseAppPrivateStorage(permissionSet)
        val showAppFolderChoice = viewModel.choosingAppFolder || canUseAppFolder
        binding.privateStorageExplanation.isVisible = showAppFolderChoice
        binding.continueButton.apply {
            isEnabled = !completingStorage && (hasRequiredPermissions || canUseAppFolder)
            setText(if (showAppFolderChoice) CommonString.use_app_folder else CommonString.dialog_continue)
        }
        binding.headline.setText(
            if (showAppFolderChoice) CommonString.permissions_screen_optional_headline else CommonString.permissions_screen_headline,
        )
    }

    private fun completeStorageChoice() {
        val completion = viewModel.completeStorageChoice(permissionSet)
        refreshPermissions()
        completion?.let(::observeStorageCompletion)
    }

    private fun observeStorageCompletion(completion: StorageCompletion) {
        // A later choice replaces the observer, while its old worker may still finish on the collection queue.
        completionObserver?.cancel()
        completionObserver =
            launchCatchingTask {
                try {
                    val accepted = completion.result.await()
                    withStarted {
                        if (accepted) {
                            permissionSet = currentPermissions()
                            if (permissionSet.hasRequiredPermissions(this@PermissionsActivity)) {
                                finish()
                            } else {
                                // Internet may have been revoked after the storage path was saved.
                                showPermissionsFragment()
                            }
                        }
                    }
                } catch (e: SystemStorageException) {
                    Timber.w(e, "unable to obtain an app-private directory")
                    withStarted { showThemedToast(this@PermissionsActivity, CommonString.something_wrong, false) }
                } catch (e: StorageAccessException) {
                    Timber.w(e, "unable to create an app-private directory")
                    withStarted { showThemedToast(this@PermissionsActivity, CommonString.something_wrong, false) }
                } finally {
                    // A destroyed activity must leave the result for its replacement to consume.
                    if (currentCoroutineContext().isActive) {
                        viewModel.storageCompletionHandled(completion)
                        if (!isFinishing) refreshPermissions()
                    }
                }
            }
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
