/*
 *  Copyright (c) 2025 Akshita Tiwary <akshita.andev16@gmail.com>
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

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.utils.Permissions

class InternetPermissionFragment : PermissionsFragment(R.layout.internet_permission_fragment) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        view
            .findViewById<PermissionsItem>(R.id.internet_permission)
            .initializeInternetPermissionItem()
    }
}
