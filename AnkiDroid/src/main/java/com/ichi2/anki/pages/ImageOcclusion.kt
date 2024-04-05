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
import android.view.View
import android.webkit.WebView
import androidx.core.os.bundleOf
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ImageOcclusionActivity
import com.ichi2.anki.R
import org.json.JSONObject
import timber.log.Timber

class ImageOcclusion : PageFragment(R.layout.image_occlusion) {

    override val title: String
        get() = TR.notetypesImageOcclusionName()
    override val pageName = "image-occlusion"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            if (it.itemId == R.id.action_save) {
                Timber.i("save item selected")
                webView.evaluateJavascript("anki.imageOcclusion.addNote()", null)
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onCreateWebViewClient(savedInstanceState: Bundle?): PageWebViewClient {
        val kind = arguments?.getString(ARG_KEY_KIND) ?: throw Exception("missing kind")
        val id = arguments?.getLong(ARG_KEY_ID) ?: throw Exception("missing ID")
        val path = arguments?.getString(ARG_KEY_PATH) ?: if (kind == "add") throw Exception("missing path") else ""
        return ImageOcclusionWebViewClient(kind, id, path)
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
            return ImageOcclusionActivity.getIntent(context, ImageOcclusion::class, arguments)
        }
    }
}
