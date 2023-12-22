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

import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionsActivityTest : RobolectricTest() {
    @Test
    fun `Each screen starts normally and has the same permissions of a PermissionSet`() {
        ActivityScenario.launch(PermissionsActivity::class.java).onActivity { activity ->
            for (permissionSet in PermissionSet.entries) {
                val fragment = permissionSet.permissionsFragment?.getDeclaredConstructor()?.newInstance() ?: continue
                activity.supportFragmentManager.commitNow {
                    replace(R.id.fragment_container, fragment)
                }
                val allPermissions = fragment.permissionItems.flatMap { it.permissions }

                assertThat(permissionSet.permissions, containsInAnyOrder(allPermissions))
            }
        }
    }
}
