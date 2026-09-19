// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.annotation.CheckResult
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.search.SearchNode
import com.ichi2.anki.Flag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection

/** Change the flag color of the specified cards. */
@CheckResult
fun Collection.setUserFlagForCards(
    cids: Iterable<Long>,
    flag: Flag,
): OpChangesWithCount = setUserFlagForCards(cids, flag.code)

/**
 * The `Custom scheduling` global setting in deck options.
 */
var Collection.cardStateCustomizer: String
    get() = config.getString(ConfigKey.String.CARD_STATE_CUSTOMIZER)
    set(value) {
        config.setString(ConfigKey.String.CARD_STATE_CUSTOMIZER, value)
    }

/** @see Collection.getCard */
fun Collection.getCardOrNull(id: CardId): Card? = runCatching { getCard(id) }.getOrNull()

/**
 * Constructs a search string from a [SearchNode].
 *
 * @see Collection.buildSearchString
 */
fun Collection.buildSearchString(node: SearchNode): String = buildSearchString(listOf(node))
