/****************************************************************************************
 * Copyright (c) 2022 lukstbit <lukstbit@users.noreply.github.com>                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.dialogs

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecursiveMenuItem(
    @StringRes val titleResourceId: Int,
    @DrawableRes val iconResourceId: Int,
    val analyticsId: String,
    val id: Long,
    val parentId: Long? = null,
    val shouldBeVisible: Boolean,
    val action: RecursiveMenuItemAction
) : Parcelable

sealed class RecursiveMenuItemAction : Parcelable {
    @Parcelize
    object Header : RecursiveMenuItemAction()

    @Parcelize
    data class OpenUrl(val url: String) : RecursiveMenuItemAction()

    @Parcelize
    data class OpenUrlResource(@StringRes val urlResourceId: Int) : RecursiveMenuItemAction()

    @Parcelize
    object ReportError : RecursiveMenuItemAction()

    @Parcelize
    object Rate : RecursiveMenuItemAction()

    @Parcelize
    data class Importer(val multiple: Boolean = false) : RecursiveMenuItemAction()
}
