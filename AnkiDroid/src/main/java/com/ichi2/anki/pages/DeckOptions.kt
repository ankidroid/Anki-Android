/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.updateDeckConfigsRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeckOptions : PageFragment() {
    override val title = R.string.menu__deck_options
    override val pageName = "deck-options"
    override lateinit var webViewClient: PageWebViewClient
    override var webChromeClient = PageChromeClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        val deckId = arguments?.getLong(ARG_DECK_ID)
            ?: throw Exception("missing deck ID")
        webViewClient = DeckOptionsWebClient(deckId)
        super.onCreate(savedInstanceState)
    }

    class DeckOptionsWebClient(val deckId: Long) : PageWebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            // from upstream: https://github.com/ankitects/anki/blob/678c354fed4d98c0a8ef84fb7981ee085bd744a7/qt/aqt/deckoptions.py#L55
            view!!.evaluateJavascript("const \$deckOptions = anki.setupDeckOptions($deckId);") {
                super.onPageFinished(view, url)
            }
        }
    }

    companion object {
        const val ARG_DECK_ID = "deckId"

        fun getIntent(context: Context, deckId: Long): Intent {
            val arguments = Bundle().apply {
                putLong(ARG_DECK_ID, deckId)
            }
            return PagesActivity.getIntent(context, DeckOptions::class, arguments)
        }
    }
}

suspend fun PagesActivity.updateDeckConfigsRaw(input: ByteArray): ByteArray {
    val output = CollectionManager.withCol { (this as CollectionV16).updateDeckConfigsRaw(input) }
    undoableOp { OpChanges.parseFrom(output) }
    withContext(Dispatchers.Main) { finish() }
    return output
}
