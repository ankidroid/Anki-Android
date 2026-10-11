// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.common.utils.configProperty
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
import com.ichi2.anki.common.utils.ext.AddingDefaultsMode.USE_CURRENT_DECK
import com.ichi2.anki.libanki.Config

/**
 * How the initial deck and note type are chosen when adding a note.
 *
 * @see USE_CURRENT_DECK
 * @see DECIDE_BY_NOTE_TYPE
 */
enum class AddingDefaultsMode {
    /** Start with the selected study deck and its remembered note type. */
    USE_CURRENT_DECK,

    /** Start with the current note type and its remembered destination deck. */
    DECIDE_BY_NOTE_TYPE,
}

// TODO: Extract module-specific config to modules (when they exist).

/**
 * @see AddingDefaultsMode
 */
var Config.addingDefaultsMode by configProperty(ConfigKey.Bool.ADDING_DEFAULTS_TO_CURRENT_DECK).mapped(
    decode = { if (it) USE_CURRENT_DECK else DECIDE_BY_NOTE_TYPE },
    encode = { it == USE_CURRENT_DECK },
)

/**
 * When enabled, simple text searches automatically ignore accents.
 *
 * When enabled, both 'uber' and 'über' match `["uber", "über", "Über"]`.
 *
 * [Manual: Searching - Ignoring accents/combining characters](https://docs.ankiweb.net/searching.html#ignoring-accentscombining-characters)
 *
 * [Added in Anki#1667](https://github.com/ankitects/anki/pull/1667)
 */
var Config.ignoreAccentsInSearch by configProperty(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH)

/** Whether pasted images are saved as PNG. */
var Config.pasteImagesAsPng by configProperty(ConfigKey.Bool.PASTE_IMAGES_AS_PNG)

/** Whether rendered cards hide their audio play buttons. */
val Config.hideAudioPlayButtons by configProperty(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS)
