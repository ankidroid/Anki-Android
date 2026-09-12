package com.ichi2.anki.servicelayer

import android.content.Context
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import com.ichi2.anki.common.coroutines.applicationScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object StartupWebViewService {
    private var startUpWebViewDeferred: Deferred<Result<Unit>>? = null
    private val mutex = Mutex()

    /**
     * Ensure only one startup at the same time.
     * Fatal error if fails.
     * So no need for another wait for completion function or reset the deferred.
     */
    suspend fun startUpWebView(context: Context): Result<Unit> {
        val deferred =
            mutex.withLock {
                startUpWebViewDeferred?.let { return@withLock it }

                val newJob =
                    applicationScope.async {
                        startUpWebViewPrivate(context)
                    }

                startUpWebViewDeferred = newJob
                newJob
            }

        return deferred.await()
    }

    private suspend fun startUpWebViewPrivate(context: Context): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val executor = Executors.newSingleThreadExecutor()
            val config = WebViewStartUpConfig.Builder(executor).build()

            val callback =
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult?) {
                        executor.shutdown()
                        continuation.resume(Result.success(Unit))
                    }

                    override fun onError(e: WebViewStartupException) {
                        executor.shutdown()
                        continuation.resume(Result.failure(e))
                    }
                }

            WebViewCompat.startUpWebView(context, config, callback)
        }
}
