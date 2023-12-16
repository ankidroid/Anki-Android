/****************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.hideShowButtonCss

class AnkiPackageImporterFragment : PageFragment() {
    override val title: String
        get() = resources.getString(R.string.menu_import)
    override val pageName: String
        get() = "import-page"
    override lateinit var webViewClient: PageWebViewClient
    override var webChromeClient: PageChromeClient = PageChromeClient()
    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        // the back callback is only enabled when import is running and showing progress
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                CollectionManager.getBackend().setWantsAbort()
                // once triggered the callback is not needed as the import process can't be resumed
                remove()
            }
        }
        val path = arguments?.getString(ARG_FILE_PATH)
            ?: throw IllegalStateException("No path provided for apkg package to import")
        webViewClient = AnkiPackageImporterWebViewClient(path, backCallback)
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, backCallback)
    }

    class AnkiPackageImporterWebViewClient(
        private val path: String,
        private val backCallback: OnBackPressedCallback,
    ) : PageWebViewClient() {
        /**
         * Ideally, to handle the state of the back callback, we would just need to check for
         * `/latestProgress` calls followed by one `/importDone` call. However there are some extra
         * calls to `/latestProgress` AFTER `/importDone` and this property keeps track of this.
         */
        private var isDone = false

        override fun onPageFinished(view: WebView?, url: String?) {
            val params = """{ type: "json_file", path: "$path"}"""
            // https://github.com/ankitects/anki/blob/main/ts/import-page/index.ts
            view!!.evaluateJavascript("anki.setupImportPage($params);$hideShowButtonCss;") {
                super.onPageFinished(view, url)
            }
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            super.onLoadResource(view, url)
            backCallback.isEnabled = when {
                url == null -> false
                url.endsWith("latestProgress") && !isDone -> true
                url.endsWith("importDone") -> {
                    isDone = true // import was done so disable any back callback changes after this call
                    false
                }
                else -> false
            }
        }
    }

    companion object {
        private const val ARG_FILE_PATH = "arg_file_path"

        fun getIntent(context: Context, filePath: String): Intent {
            val args = bundleOf(ARG_FILE_PATH to filePath)
            return PagesActivity.getIntent(context, AnkiPackageImporterFragment::class, args)
        }
    }
}
