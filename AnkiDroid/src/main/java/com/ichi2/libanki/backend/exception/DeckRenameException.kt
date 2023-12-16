//noinspection MissingCopyrightHeader #8659
package com.ichi2.libanki.backend.exception

class DeckRenameException(val errorCode: Int) : Exception() {
    // region only if FILTERED_NOSUBDECKS
    private var filteredAncestorName: String? = null
    private var deckName: String? = null
    // endregion

    override val message: String?
        get() = if (errorCode == FILTERED_NOSUBDECKS) {
            "Deck $deckName has filtered ancestor $filteredAncestorName"
        } else {
            super.message
        }

    companion object {
        const val ALREADY_EXISTS = 0
        const val FILTERED_NOSUBDECKS = 1

        /** Generates a [DeckRenameException] with additional information in the message */
        fun filteredAncestor(
            deckName: String?,
            filteredAncestorName: String?,
        ): DeckRenameException = DeckRenameException(FILTERED_NOSUBDECKS).apply {
            this.filteredAncestorName = filteredAncestorName
            this.deckName = deckName
        }
    }
}
