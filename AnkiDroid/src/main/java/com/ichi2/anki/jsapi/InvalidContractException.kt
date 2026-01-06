/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A

 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.jsapi

import android.content.res.Resources
import com.ichi2.anki.R

sealed class InvalidContractException : Exception() {
    abstract val error: JsApiError

    abstract fun localizedErrorMessage(resources: Resources): String

    fun errorMessage(resources: Resources): String = resources.getString(R.string.js_api_error_code, error.code)

    class ContactError : InvalidContractException() {
        override val error = JsApiError.InvalidContact

        override fun localizedErrorMessage(resources: Resources): String =
            resources.getString(R.string.invalid_contact_message, errorMessage(resources))
    }

    class VersionError(
        private val requestVersion: String,
        private val contact: String,
    ) : InvalidContractException() {
        override val error = JsApiError.InvalidVersion

        override fun localizedErrorMessage(resources: Resources): String =
            resources.getString(R.string.invalid_js_api_version_message, requestVersion, contact, errorMessage(resources))
    }

    class OutdatedVersion(
        private val currentVersion: String,
        private val requestVersion: String,
        private val contact: String,
    ) : InvalidContractException() {
        override val error = JsApiError.OutdatedVersion

        override fun localizedErrorMessage(resources: Resources): String =
            resources.getString(R.string.outdated_js_api_message, currentVersion, requestVersion, contact, errorMessage(resources))
    }
}
