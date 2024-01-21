/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import com.afollestad.materialdialogs.MaterialDialog

// Extension methods for MaterialDialog workarounds in Kotlin
// Previously the methods accepted null into a non-null parameter,
// and fixing this would break the fluent interface

fun MaterialDialog.contentNullable(message: CharSequence?): MaterialDialog {
    message?.let { this.message(text = it) }
    return this
}
