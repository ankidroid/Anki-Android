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
import androidx.core.os.bundleOf
import com.ichi2.anki.R
import com.ichi2.libanki.CardId

class CardInfo : PageFragment() {
    override val title: String
        get() = resources.getString(R.string.card_info_title)

    override val pageName = "card-info"
    override lateinit var webViewClient: PageWebViewClient
    override var webChromeClient = PageChromeClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        val cardId = arguments?.getLong(ARG_CARD_ID)
            ?: throw Exception("missing card ID argument")
        webViewClient = CardInfoWebClient(cardId)
        super.onCreate(savedInstanceState)
    }

    class CardInfoWebClient(val cardId: Long) : PageWebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            // from upstream: https://github.com/ankitects/anki/blob/678c354fed4d98c0a8ef84fb7981ee085bd744a7/qt/aqt/browser/card_info.py#L66-L72
            view!!.evaluateJavascript("anki.cardInfoPromise = anki.setupCardInfo(document.body);", null)
            view.evaluateJavascript("anki.cardInfoPromise.then((c) => c.updateStats($cardId));") {
                super.onPageFinished(view, url)
            }
        }
    }

    companion object {
        private const val ARG_CARD_ID = "cardId"

        fun CardInfoDestination.toIntent(context: Context): Intent =
            PagesActivity.getIntent(context, CardInfo::class, bundleOf(ARG_CARD_ID to cardId))
    }
}

data class CardInfoDestination(val cardId: CardId)
