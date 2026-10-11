// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config

// Extensions for com.ichi2.anki.libanki.Config

/**
 * Default search text for the card browser
 *
 * e.g. "deck:current"
 */
var Config.defaultBrowserSearch
    get() = getString(ConfigKey.String.DEFAULT_SEARCH_TEXT)
    set(value) = setString(ConfigKey.String.DEFAULT_SEARCH_TEXT, value)
