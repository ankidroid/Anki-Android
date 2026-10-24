// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config

// Extensions for com.ichi2.anki.libanki.Config

/**
 * When enabled, simple text searches automatically ignore accents.
 *
 * When enabled, both 'uber' and 'über' match `["uber", "über", "Über"]`.
 *
 * The default for `IGNORE_ACCENTS_IN_SEARCH` is `false` upstream.
 *
 * [Manual: Searching - Ignoring accents/combining characters](https://docs.ankiweb.net/searching.html#ignoring-accentscombining-characters)
 * [Added in Anki#1667](https://github.com/ankitects/anki/pull/1667)
 * [source](https://github.com/ankitects/anki/blob/main/rslib/src/config/bool.rs)
 */
var Config.ignoreAccentsInSearch
    get() = getBool(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH)
    set(value) = setBool(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH, value)

/**
 * Default search text for the card browser
 *
 * e.g. "deck:current"
 *
 * The default for `DEFAULT_SEARCH_TEXT` is `"deck:current"` upstream.
 *
 * [source](https://github.com/ankitects/anki/blob/main/rslib/src/config/string.rs)
 */
var Config.defaultBrowserSearch
    get() = getString(ConfigKey.String.DEFAULT_SEARCH_TEXT)
    set(value) = setString(ConfigKey.String.DEFAULT_SEARCH_TEXT, value)
