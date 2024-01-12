/*
 *  Copyright (c) 2023 Abdo <abdo@abdnh.net>
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
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.SingleFragmentActivity
import org.json.JSONObject

class ImageOcclusion : PageFragment() {

    override val title: String
        get() = TR.notetypesImageOcclusionName()
    override val pageName = "image-occlusion"
    override lateinit var webViewClient: PageWebViewClient

    override fun onCreate(savedInstanceState: Bundle?) {
        val kind = arguments?.getString(ARG_KEY_KIND) ?: throw Exception("missing kind")
        val id = arguments?.getLong(ARG_KEY_ID) ?: throw Exception("missing ID")
        val path = arguments?.getString(ARG_KEY_PATH) ?: if (kind == "add") throw Exception("missing path") else ""
        webViewClient = ImageOcclusionWebViewClient(kind, id, path)
        super.onCreate(savedInstanceState)
    }

    class ImageOcclusionWebViewClient(val kind: String, private val noteOrNotetypeId: Long, private val path: String?) : PageWebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val options = JSONObject()
            options.put("kind", kind)
            options.put("imagePath", path)
            if (kind == "add") {
                options.put("notetypeId", noteOrNotetypeId)
            } else {
                options.put("noteId", noteOrNotetypeId)
            }
            view!!.evaluateJavascript("anki.setupImageOcclusion($options);") {
                super.onPageFinished(view, url)
            }
        }
    }

    companion object {
        private const val ARG_KEY_KIND = "kind"
        private const val ARG_KEY_ID = "id"
        private const val ARG_KEY_PATH = "path"

        fun getIntent(context: Context, kind: String, noteOrNotetypeId: Long, imagePath: String?): Intent {
            val arguments = bundleOf(ARG_KEY_KIND to kind, ARG_KEY_ID to noteOrNotetypeId, ARG_KEY_PATH to imagePath)
            return SingleFragmentActivity.getIntent(context, ImageOcclusion::class, arguments)
        }
    }
}
