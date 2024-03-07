package com.ichi2.anki.servicelayer

import com.ichi2.libanki.sched.DeckNode
import java.util.Locale

object DeckService {
    var dueTree: DeckNode? = null

    fun deckExists(tree: DeckNode, deckName: String) : Boolean {
        val newDeckName = deckName.lowercase(Locale.getDefault())
        if(newDeckName == "default") {
            return true
        }
        for(child in tree.children) {
            if(child.fullDeckName.lowercase(Locale.getDefault()) == newDeckName) {
                return true
            }
        }
        return false
    }
}
