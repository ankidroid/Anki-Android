package com.ichi2.anki.ui.windows.permissions

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.core.net.toUri
import com.ichi2.anki.R

/**
 * A PermissionsFragment that informs the user about the requirement for Internet access,
 * specifically for localhost communication (e.g., in environments like GrapheneOS or Xiaomi).
 *
 * This screen is shown via PermissionsActivity and explains that although no runtime
 * INTERNET permission is needed, disabling Internet manually can break app functionality.
 *
 * The user is provided with a switch-style PermissionItem that:
 * - Opens the app settings screen to re-enable Internet access
 * - Enables the continue button in PermissionsActivity after acknowledgment
 */
class InternetInfoFragment : PermissionsFragment(R.layout.permission_internet_xiomi) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val internetPermissionItem =
            view.findViewById<PermissionItem>(R.id.internet_permission)

        internetPermissionItem.setOnSwitchClickListener {
            // Optionally open settings
            openAppSettings()

            // Enable the continue button in PermissionsActivity
            (activity as? PermissionsActivity)?.setContinueButtonEnabled(true)
        }
    }

    private fun openAppSettings() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = ("package:" + requireContext().packageName).toUri()
            }
        startActivity(intent)
    }
}
