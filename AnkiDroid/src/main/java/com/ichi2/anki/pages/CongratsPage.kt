/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
import android.view.View
import android.webkit.JavascriptInterface
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.SingleFragmentActivity

class CongratsPage : PageFragment() {
    override val title: String = ""
    override val pageName = "congrats"

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        return super.onCreateWebViewClient(savedInstanceState).also { client ->
            client.onPageFinishedCallback = OnPageFinishedCallback { webView ->
                webView.evaluateJavascript(
                    "bridgeCommand = function(request){ ankidroid.bridgeCommand(request); };"
                ) {}
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView.addJavascriptInterface(BridgeCommand(), "ankidroid")
    }

    inner class BridgeCommand {
        @JavascriptInterface
        fun bridgeCommand(request: String) {
            TODO("$request will be handled in the next commit ;)")
        }
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return SingleFragmentActivity.getIntent(context, CongratsPage::class)
        }
    }
}
