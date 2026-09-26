// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import androidx.appcompat.app.AppCompatDelegate
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.utils.LanguageUtil

/**
 * Closes and reopens the backend using the provided [language], typically for
 * [CollectionManager.TR] calls
 *
 * This does not set the [application locales][AppCompatDelegate.setApplicationLocales]
 *
 * @param language tag in the form: `de` or `zh-CN`
 */
@Suppress("UnusedReceiverParameter")
suspend fun Collection.reopenWithLanguage(language: String) {
    LanguageUtil.setDefaultBackendLanguages(language)
    CollectionManager.discardBackend()
    CollectionManager.getColUnsafe()
}
