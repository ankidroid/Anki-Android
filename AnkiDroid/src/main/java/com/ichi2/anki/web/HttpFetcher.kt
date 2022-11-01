/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.anki.web
import android.content.Context
import com.ichi2.async.Connection
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.sync.Tls12SocketFactory
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.VersionUtils.pkgVersionName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Helper class to download from web.
 * <p>
 * Used in AsyncTasks in Translation and Pronunciation activities, and more...
 */
object HttpFetcher {
    /**
     * Get an OkHttpClient configured with correct timeouts and headers
     *
     * @param fakeUserAgent true if we should issue "fake" User-Agent header 'Mozilla/5.0' for compatibility
     * @return OkHttpClient.Builder ready for use or further configuration
     */
    fun getOkHttpBuilder(fakeUserAgent: Boolean): OkHttpClient.Builder {
        val clientBuilder = OkHttpClient.Builder()
        Tls12SocketFactory.enableTls12OnPreLollipop(clientBuilder)
            .connectTimeout(Connection.CONN_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(Connection.CONN_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(Connection.CONN_TIMEOUT.toLong(), TimeUnit.SECONDS)
        if (fakeUserAgent) {
            clientBuilder.addNetworkInterceptor(
                Interceptor { chain: Interceptor.Chain ->
                    chain.proceed(
                        chain.request()
                            .newBuilder()
                            .header("Referer", "com.ichi2.anki")
                            .header("User-Agent", "Mozilla/5.0 ( compatible ) ")
                            .header("Accept", "*/*")
                            .build()
                    )
                }
            )
        } else {
            clientBuilder.addNetworkInterceptor(
                Interceptor { chain: Interceptor.Chain ->
                    chain.proceed(
                        chain.request()
                            .newBuilder()
                            .header("User-Agent", "AnkiDroid-$pkgVersionName")
                            .build()
                    )
                }
            )
        }
        return clientBuilder
    }

    fun fetchThroughHttp(address: String?, encoding: String? = "utf-8"): String {
        Timber.d("fetching %s", address)
        var response: Response? = null
        return try {
            val requestBuilder = Request.Builder()
            requestBuilder.url(address!!).get()
            val httpGet: Request = requestBuilder.build()
            val client: OkHttpClient = getOkHttpBuilder(true).build()
            response = client.newCall(httpGet).execute()
            if (response.code != 200) {
                Timber.d("Response code was %s, returning failure", response.code)
                return "FAILED"
            }
            val reader = BufferedReader(
                InputStreamReader(
                    response.body!!.byteStream(),
                    Charset.forName(encoding)
                )
            )
            val stringBuilder = StringBuilder()
            var line: String?
            @KotlinCleanup("it's strange")
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            stringBuilder.toString()
        } catch (e: Exception) {
            Timber.d(e, "Failed with an exception")
            "FAILED with exception: " + e.message
        } finally {
            response?.body?.close()
        }
    }

    fun downloadFileToSdCard(UrlToFile: String, context: Context, prefix: String?): String {
        var str = downloadFileToSdCardMethod(UrlToFile, context, prefix, "GET")
        if (str.startsWith("FAIL")) {
            str = downloadFileToSdCardMethod(UrlToFile, context, prefix, "POST")
        }
        return str
    }

    private fun downloadFileToSdCardMethod(UrlToFile: String, context: Context, prefix: String?, method: String): String {
        var response: Response? = null
        return try {
            val url = URL(UrlToFile)
            val extension = UrlToFile.substring(UrlToFile.length - 4)
            val requestBuilder = Request.Builder()
            requestBuilder.url(url)
            if ("GET" == method) {
                requestBuilder.get()
            } else {
                requestBuilder.post(ByteArray(0).toRequestBody(null, 0, 0))
            }
            val request: Request = requestBuilder.build()
            val client: OkHttpClient = getOkHttpBuilder(true).build()
            response = client.newCall(request).execute()
            val file = File.createTempFile(prefix!!, extension, context.cacheDir)
            val inputStream = response.body!!.byteStream()
            CompatHelper.compat.copyFile(inputStream, file.canonicalPath)
            inputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Timber.w(e)
            "FAILED " + e.message
        } finally {
            response?.body?.close()
        }
    }
}
