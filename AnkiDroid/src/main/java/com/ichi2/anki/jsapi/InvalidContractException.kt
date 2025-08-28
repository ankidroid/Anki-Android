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
    abstract fun localizedErrorMessage(resources: Resources): String

    class ContactError : InvalidContractException() {
        override fun localizedErrorMessage(resources: Resources): String {
            val errorMessage = resources.getString(R.string.js_api_error_code, INVALID_CONTACT_ERROR_CODE)
            return resources.getString(R.string.invalid_contact_message, errorMessage)
        }
    }

    class VersionError(
        private val requestVersion: String,
        private val contact: String,
    ) : InvalidContractException() {
        override fun localizedErrorMessage(resources: Resources): String {
            val errorMessage = resources.getString(R.string.js_api_error_code, INVALID_VERSION_ERROR_CODE)
            return resources.getString(R.string.invalid_js_api_version_message, requestVersion, contact, errorMessage)
        }
    }

    class OutdatedVersion(
        private val currentVersion: String,
        private val requestVersion: String,
        private val contact: String,
    ) : InvalidContractException() {
        override fun localizedErrorMessage(resources: Resources): String {
            val errorMessage = resources.getString(R.string.js_api_error_code, OUTDATED_VERSION_ERROR_CODE)
            return resources.getString(R.string.outdated_js_api_message, currentVersion, requestVersion, contact, errorMessage)
        }
    }

    companion object {
        const val INVALID_CONTACT_ERROR_CODE = "INVALID_CONTACT"
        const val INVALID_VERSION_ERROR_CODE = "INVALID_VERSION"
        const val OUTDATED_VERSION_ERROR_CODE = "OUTDATED_VERSION"
    }
}
