// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import com.ichi2.anki.IntentHandler.Companion.EXTRA_DECK_ID
import com.ichi2.anki.IntentHandler.Companion.EXTRA_PROFILE_ID
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.multiprofile.ProfileId

/** Builds the [Intent] that opens the reviewer for this destination. */
fun ReviewDeckDestination.toIntent(context: Context): Intent =
    when (this) {
        is ReviewDeckDestination.CurrentDeck -> Reviewer.getIntent(context)
        is ReviewDeckDestination.ExternalLaunch ->
            Intent(context, IntentHandler::class.java).apply {
                // shortcuts require the intent to have an action
                setAction(Intent.ACTION_VIEW)
                putExtra(EXTRA_DECK_ID, deckId)
                putDeckProfile(context)
            }
    }

fun Intent.putDeckProfile(context: Context): Intent =
    apply {
        val profileId = AnkiDroidApp.activeProfileId(context)
        if (!profileId.isDefault()) putExtra(EXTRA_PROFILE_ID, profileId.value)
    }

fun Intent.getDeckProfile(): ProfileId = getStringExtra(EXTRA_PROFILE_ID)?.let(::ProfileId) ?: ProfileId.DEFAULT

fun Context.otherProfileDeckMessage(profileId: ProfileId): String {
    val profileName =
        AnkiDroidApp.instance.profileManager
            ?.getAllProfiles()
            ?.get(profileId)
            ?.displayName
            ?.value
            ?: return getString(CommonString.deck_shortcut_doesnt_exist)
    return getString(CommonString.deck_in_other_profile, profileName)
}

fun Context.deckShortcutId(deckId: DeckId): String {
    val profileId = AnkiDroidApp.activeProfileId(this)
    return if (profileId.isDefault()) deckId.toString() else "${profileId.value}_$deckId"
}
