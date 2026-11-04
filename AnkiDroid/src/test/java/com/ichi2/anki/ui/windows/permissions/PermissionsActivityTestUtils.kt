// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import kotlin.test.assertTrue

fun PermissionsActivity.chooseAppFolder() {
    assertTrue(binding.continueWithoutAccess.isEnabled)
    binding.continueWithoutAccess.performClick()
}
