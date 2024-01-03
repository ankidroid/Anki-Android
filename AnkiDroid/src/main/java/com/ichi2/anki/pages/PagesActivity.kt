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
import androidx.fragment.app.commit
import com.ichi2.anki.*
import com.ichi2.utils.getInstanceFromClassName
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Container activity to host Anki HTML pages
 * Responsibilities:
 * * Serve as parent activity of the [PageFragment] that holds the page
 * * Host an [AnkiServer] to intercept any requests made by an Anki page and resolve them
 * * Operate UI requests by the [AnkiServer]
 */
class PagesActivity : AnkiActivity(), PostRequestHandler {
    private lateinit var ankiServer: AnkiServer

    fun baseUrl(): String {
        return ankiServer.baseUrl()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.page_activity)
        enableToolbar()

        // Enable debugging on DEBUG builds
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        // Load server
        ankiServer = AnkiServer(this)
        ankiServer.start()

        // Launch page
        val pageClassName = intent.extras?.getString(EXTRA_PAGE_CLASS)
            ?: throw Exception("PageActivity's intent should have a '$EXTRA_PAGE_CLASS' extra")

        val pageFragment = getInstanceFromClassName<PageFragment>(pageClassName).apply {
            arguments = intent.getBundleExtra(EXTRA_PAGE_ARGS)
        }
        supportFragmentManager.addFragmentOnAttachListener { _, _ -> this.title = pageFragment.title }
        supportFragmentManager.commit {
            replace(R.id.page_container, pageFragment)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        /** Stop running the server if the activity is destroyed.
         * The initialization check is for the case [showedActivityFailedScreen] is true */
        if (this::ankiServer.isInitialized && ankiServer.isAlive) {
            ankiServer.stop()
        }
    }

    override suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray {
        val methodName = if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
            uri.substring(AnkiServer.ANKI_PREFIX.length)
        } else {
            throw IllegalArgumentException("unhandled request: $uri")
        }
        return handleUiPostRequest(methodName, bytes)
            ?: handleCollectionPostRequest(methodName, bytes)
            ?: throw IllegalArgumentException("unhandled method: $methodName")
    }

    companion object {
        /**
         * Extra key of [PagesActivity]'s intent that can be used to pass a [Bundle]
         * as arguments of the [PageFragment] that will be opened
         */
        const val EXTRA_PAGE_ARGS = "pageArgs"

        /**
         * Extra key of [PagesActivity]'s intent that must be included and
         * hold the name of an [Anki HTML page](https://github.com/ankitects/anki/tree/main/ts)
         */
        const val EXTRA_PAGE_CLASS = "pageClass"

        /**
         * @param fragmentClass class of the [PageFragment] to be created
         * @param arguments to be passed to the created [PageFragment]
         */
        fun getIntent(context: Context, fragmentClass: KClass<out PageFragment>, arguments: Bundle? = null): Intent {
            return Intent(context, PagesActivity::class.java).apply {
                putExtra(EXTRA_PAGE_CLASS, fragmentClass.jvmName)
                putExtra(EXTRA_PAGE_ARGS, arguments)
            }
        }
    }
}
