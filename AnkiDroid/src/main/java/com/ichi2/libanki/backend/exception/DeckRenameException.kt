//noinspection MissingCopyrightHeader #8659
package com.ichi2.libanki.backend.exception

import android.content.res.Resources
import com.ichi2.anki.R

class DeckRenameException
(private val errorCode: Int) : Exception() {
    // region only if FILTERED_NOSUBDECKS
    private var mFilteredAncestorName: String? = null
    private var mDeckName: String? = null
    // endregion

    override val message: String?
        get() = if (errorCode == FILTERED_NOSUBDECKS) {
            "Deck $mDeckName has filtered ancestor $mFilteredAncestorName"
        } else {
            super.message
        }

    fun getLocalizedMessage(res: Resources): String {
        return when (errorCode) {
            ALREADY_EXISTS -> res.getString(R.string.decks_rename_exists)
            FILTERED_NOSUBDECKS -> res.getString(R.string.decks_rename_filtered_nosubdecks)
            else -> ""
        }
    }

    companion object {
        const val ALREADY_EXISTS = 0
        private const val FILTERED_NOSUBDECKS = 1

        /** Generates a {@link com.ichi2.libanki.backend.exception.DeckRenameException} with additional information in the message */
        fun filteredAncestor(deckName: String?, filteredAncestorName: String?): DeckRenameException {
            val ex = DeckRenameException(FILTERED_NOSUBDECKS)
            ex.mFilteredAncestorName = filteredAncestorName
            ex.mDeckName = deckName
            return ex
        }
    }
}
