// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.jsapi.legacy

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiDroidJsAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Connects the server to a view-owned API instance. Requests run in the view's scope, so destroying
 * the view cancels outstanding work and releases the host.
 */
class LegacyJsApiBridge {
    private var owner: LifecycleOwner? = null
    private var api: AnkiDroidJsAPI? = null
    private var observer: DefaultLifecycleObserver? = null

    @MainThread
    fun attach(
        owner: LifecycleOwner,
        host: LegacyJsApiHost,
    ) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        detach()

        val api = AnkiDroidJsAPI(host)
        val observer =
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    detach()
                }
            }

        this.owner = owner
        this.api = api
        this.observer = observer
        owner.lifecycle.addObserver(observer)
    }

    @MainThread
    fun detach() {
        api?.close()
        observer?.let { owner?.lifecycle?.removeObserver(it) }
        owner = null
        api = null
        observer = null
    }

    suspend fun handleRequest(
        method: String,
        bytes: ByteArray,
    ): ByteArray =
        withContext(Dispatchers.Main) {
            val api = api
            val owner = owner
            if (api == null || owner == null) {
                return@withContext studyScreenUnavailable()
            }
            try {
                owner.lifecycleScope
                    .async {
                        api.handleJsApiRequest(method, bytes, returnDefaultValues = false)
                    }.await()
            } catch (_: CancellationException) {
                studyScreenUnavailable()
            }
        }

    private fun studyScreenUnavailable(): ByteArray =
        AnkiDroidJsAPI.ApiResult
            .failure("Study screen unavailable")
            .toString()
            .toByteArray()
}
