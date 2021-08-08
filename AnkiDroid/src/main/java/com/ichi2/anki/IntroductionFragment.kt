/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
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

package com.ichi2.anki

import android.content.res.AssetManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.ichi2.libanki.Utils.convertStreamToString
import org.apache.commons.text.StringEscapeUtils
import java.io.*

class IntroductionFragment : Fragment() {
    companion object {
        fun newInstance(introductionResources: IntroductionActivity.IntroductionResources): IntroductionFragment {
            val fragment = IntroductionFragment()
            val resourcesBundle = bundleOf(
                "Title" to introductionResources.title,
                "Description" to introductionResources.description,
                "Image" to introductionResources.image,
                "WebPage" to introductionResources.webPage,
                "Localization" to introductionResources.localization
            )

            fragment.arguments = resourcesBundle

            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.introduction_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getInt("Title")?.let {
            view.findViewById<TextView>(R.id.title_text)?.text = getString(it)
        }

        arguments?.getInt("Description")?.let {
            view.findViewById<TextView>(R.id.description_text)?.text = getString(it)
        }

        arguments?.getInt("Image")?.let {
            view.findViewById<WebView>(R.id.intro_webview)?.visibility = View.GONE
            view.findViewById<ImageView>(R.id.intro_image)?.apply {
                visibility = View.VISIBLE
                setImageResource(it)
            }
        }

        arguments?.getString("WebPage")?.let {
            view.findViewById<ImageView>(R.id.intro_image)?.visibility = View.GONE
            view.findViewById<WebView>(R.id.intro_webview)?.apply {
                visibility = View.VISIBLE
                settings.javaScriptEnabled = true
                val sHTML = optimiseWebView(it)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        // Get strings for replacement, return if there are none
                        val localizationStrings = arguments?.getSerializable("Localization") as List<Pair<String, Int>>? ?: return

                        localizationStrings.forEach {
                            localizeSlide(view, it.first, it.second)
                        }
                    }
                }
                loadDataWithBaseURL("file:///android_asset/", sHTML, "text/html", "utf-8", null)
            }
        }
    }

    /**
     * Get element using ID and use localized string for that element.
     */
    private fun localizeSlide(webView: WebView, textIdInHtml: String, @StringRes textResource: Int) {
        val escapedText = StringEscapeUtils.escapeEcmaScript(getString(textResource))
        webView.evaluateJavascript("document.getElementById('$textIdInHtml').innerHTML = '$escapedText'") {}
    }

    private fun optimiseWebView(webPage: String): String {
        // Optimisation to load local HTML faster
        // Source: https://stackoverflow.com/a/9990440
        val assetManager: AssetManager = requireContext().assets
        val inputStream: InputStream = assetManager.open(webPage, AssetManager.ACCESS_BUFFER)
        inputStream.use {
            val sHTML: String = convertStreamToString(it)
            it.close()
            return sHTML
        }
    }
}
