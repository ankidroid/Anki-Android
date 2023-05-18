/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.permissions

/**
 * The result of checking the status of permissions
 * @param permissions A map, containing an entry for each required permission, associating to it whether it's already granted
 */
open class PermissionsCheckResult(val permissions: Map<String, Boolean>) {
    val allGranted = permissions.all { it.value }
    val requiresPermissionDialog: Boolean = permissions.any { !it.value }

    /**
     * @return A [PermissionsRequestRawResults], or `null` if a permissions dialog is required.
     */
    fun toPermissionsRequestRawResult(): PermissionsRequestRawResults? {
        if (requiresPermissionDialog) {
            return null
        }
        return permissions
    }
}
