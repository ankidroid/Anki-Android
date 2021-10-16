/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
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
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                  *
 ***************************************************************************************/

package com.ichi2.anki.jsaddons

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import timber.log.Timber
import java.io.*
import java.util.*

class AddonConfigEditor {

    /**
     * Show webview with config.html page from addon package directory as url
     * Also create JavaScriptInterface with config.json file, for reading and writing data
     */
    fun showConfig(adddonName: String?, activity: Activity, currentAnkiDroidDirectory: String?) {
        val context: Context = activity.applicationContext

        val joinedPath = StringJoiner("/")
            .add(currentAnkiDroidDirectory)
            .add("addons")
            .add(adddonName)
            .add("package")
            .toString()

        val configHtml = StringJoiner("/").add(joinedPath).add("config.html").toString()
        val configJson = StringJoiner("/").add(joinedPath).add("config.json").toString()

        if (!File(configHtml).exists()) {
            UIUtils.showThemedToast(context, context.getString(R.string.config_not_found), true)
            return
        }

        WebView.setWebContentsDebuggingEnabled(true)

        val alert = AlertDialog.Builder(activity).create()
        val webView = WebView(context)
        webView.webViewClient = mWebViewClient
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(ConfigEditor(alert, configJson), "ConfigEditor")

        val keyboardHack = EditText(context)
        keyboardHack.visibility = View.GONE
        webView.addView(keyboardHack)

        webView.loadUrl(configHtml)
        alert.setView(webView)

        alert.setOnDismissListener {
            webView.destroy()
        }

        alert.show()
    }

    private val mWebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            view?.loadUrl(url!!)
            return true
        }
    }

    /**
     * JavaScript Interface for calling functions below in webview to save and read data
     */
    class ConfigEditor(private var alert: AlertDialog?, private var configJson: String?) {
        @JavascriptInterface
        fun save(data: String): Boolean {
            Timber.i("save::%s", data)

            try {
                val outputStreamWriter = OutputStreamWriter(FileOutputStream(File(configJson!!)))
                outputStreamWriter.write(data)
                outputStreamWriter.close()
                return true
            } catch (e: IOException) {
                Timber.w("IOException::%s", e.toString())
            }

            return false
        }

        @JavascriptInterface
        fun read(): String? {
            if (!File(configJson!!).exists()) {
                return null
            }

            val text = java.lang.StringBuilder()

            try {
                val file = File(configJson!!)
                val br = BufferedReader(FileReader(file))
                var line: String?

                while (br.readLine().also { line = it } != null) {
                    text.append(line).append("\n")
                }
                br.close()

                Timber.i("ret::%s", text.toString())
                return text.toString()
            } catch (e: FileNotFoundException) {
                Timber.w("FileNotFoundException::%s", e.toString())
            } catch (e: IOException) {
                Timber.e("IOException::%s", e.toString())
            }
            return null
        }

        @JavascriptInterface
        fun close() {
            alert?.dismiss()
        }
    }
}
