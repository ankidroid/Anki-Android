/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.account

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.isLoggedIn
import com.ichi2.utils.Permissions

class AccountActivity : SingleFragmentActivity() {
    companion object {
        /** Sees if we want to go back to the DeckPicker after login*/
        const val STAR_FROM_DECKPICKER = "START_FOR_RESULT"

        /**
         * Displays a system prompt: "Allow AnkiDroid to send you notifications"
         *
         * [launcher] receives a callback result (`boolean`) unless:
         *  * Permissions were already granted
         *  * We are < API 33
         *
         * Permissions may permanently be denied, in which case [launcher] immediately
         * receives a failure result
         */
        fun checkNotificationPermission(
            context: Context,
            launcher: ActivityResultLauncher<String>,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return
            }
            val permission = Permissions.postNotification
            if (permission != null &&
                ContextCompat.checkSelfPermission(
                    context,
                    permission,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(permission)
            }
        }

        fun getIntent(
            context: Context,
            forResult: Boolean = false,
        ): Intent =
            getIntent(
                context = context,
                fragmentClass = if (isLoggedIn()) LoggedInFragment::class else LoginFragment::class,
                arguments =
                    bundleOf(
                        STAR_FROM_DECKPICKER to forResult,
                    ),
            )
    }
}
