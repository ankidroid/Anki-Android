// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import com.ichi2.anki.IntentHandler.Companion.EXTRA_DECK_ID
import com.ichi2.anki.IntentHandler.Companion.EXTRA_PROFILE_ID
import com.ichi2.anki.common.destinations.ReviewDeckDestination
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
                putDeckProfile(profileId)
            }
    }

fun Intent.putDeckProfile(profileId: ProfileId): Intent =
    apply {
        if (!profileId.isDefault()) putExtra(EXTRA_PROFILE_ID, profileId.value)
    }

/** The [ReviewDeckDestination.ExternalLaunch] that [toIntent] wrote into this intent. */
fun Intent.toExternalLaunch() =
    ReviewDeckDestination.ExternalLaunch(
        deckId = getLongExtra(EXTRA_DECK_ID, 0),
        profileId = getStringExtra(EXTRA_PROFILE_ID)?.let(::ProfileId) ?: ProfileId.DEFAULT,
    )

/**
 * Explains why a deck from [profileId] didn't open, for example
 * "Switch to the “Work” profile to study this deck", or
 * "Deck deleted. Please remove the shortcut" if that profile no longer exists.
 */
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
