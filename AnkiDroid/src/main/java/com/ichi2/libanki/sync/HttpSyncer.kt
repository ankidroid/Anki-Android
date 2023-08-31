/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2019 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.libanki.sync

import android.content.SharedPreferences
import android.net.Uri
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.exception.UnknownHttpResponseException
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.web.CustomSyncServer
import com.ichi2.anki.web.HttpFetcher
import com.ichi2.async.Connection
import com.ichi2.libanki.Utils
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.KotlinCleanup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.net.UnknownHostException
import java.util.Locale
import java.util.Random
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import javax.net.ssl.SSLException
import kotlin.math.min

/**
 * # HTTP syncing tools
 * Calling code should catch the following codes:
 * - 501: client needs upgrade
 * - 502: ankiweb down
 * - 503/504: server too busy
 */
open class HttpSyncer(
    /**
     * Synchronization.
     */
    @KotlinCleanup("rename")
    protected val hKey: String?,
    con: Connection?,
    hostNum: HostNum
) {
    val bytesSent = AtomicLong()
    private val bytesReceived = AtomicLong()

    @Volatile
    var nextSendS: Long = 1024

    @Volatile
    var nextSendR: Long = 1024
    protected var checksumKey: String
    protected val con: Connection?
    protected var postVars: MutableMap<String, Any?>

    @Volatile
    private var mHttpClient: OkHttpClient? = null
    private val mHostNum: HostNum

    @KotlinCleanup("simplify with ?:")
    private val httpClient: OkHttpClient
        get() = if (mHttpClient != null) {
            mHttpClient!!
        } else {
            setupHttpClient()
        }

    // PERF: Thread safety isn't required for the current implementation
    @Synchronized
    private fun setupHttpClient(): OkHttpClient {
        if (mHttpClient != null) {
            return mHttpClient!!
        }
        mHttpClient = HttpFetcher.getOkHttpBuilder(false)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(null)
            .build()
        return mHttpClient!!
    }

    @Throws(UnknownHttpResponseException::class)
    fun assertOk(resp: Response?) {
        // Throw RuntimeException if HTTP error
        if (resp == null) {
            throw UnknownHttpResponseException("Null HttpResponse", -2)
        }
        val resultCode = resp.code
        if (!(resultCode == 200 || resultCode == 403)) {
            val reason = resp.message
            throw UnknownHttpResponseException(reason, resultCode)
        }
    }

    /** Note: Return value must be closed  */
    @Throws(UnknownHttpResponseException::class)
    @KotlinCleanup("use template strings")
    fun req(method: String?, fobj: InputStream? = null, comp: Int = 6): Response {
        var tmpFileBuffer: File? = null
        return try {
            val bdry = "--$BOUNDARY"
            val buf = StringWriter()
            // post vars
            postVars["c"] = if (comp != 0) 1 else 0
            for ((key, value) in postVars) {
                buf.write(bdry + "\r\n")
                buf.write(
                    String.format(
                        Locale.US,
                        "Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n",
                        key,
                        value
                    )
                )
            }
            tmpFileBuffer =
                File.createTempFile("syncer", ".tmp", File(AnkiDroidApp.cacheStorageDirectory))
            val fos = FileOutputStream(tmpFileBuffer)
            var bos = BufferedOutputStream(fos)
            val tgt: GZIPOutputStream
            // payload as raw data or json
            if (fobj != null) {
                // header
                buf.write(bdry + "\r\n")
                buf.write("Content-Disposition: form-data; name=\"data\"; filename=\"data\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                buf.close()
                bos.write(buf.toString().toByteArray(charset("UTF-8")))
                // write file into buffer, optionally compressing
                var len: Int
                val bfobj = BufferedInputStream(fobj)
                val chunk = ByteArray(65536)
                if (comp != 0) {
                    tgt = GZIPOutputStream(bos)
                    while (bfobj.read(chunk).also { len = it } >= 0) {
                        tgt.write(chunk, 0, len)
                    }
                    tgt.close()
                    bos = BufferedOutputStream(FileOutputStream(tmpFileBuffer, true))
                } else {
                    while (bfobj.read(chunk).also { len = it } >= 0) {
                        bos.write(chunk, 0, len)
                    }
                }
                bos.write("\r\n$bdry--\r\n".toByteArray(charset("UTF-8")))
            } else {
                buf.close()
                bos.write(buf.toString().toByteArray(charset("UTF-8")))
                bos.write("$bdry--\r\n".toByteArray(charset("UTF-8")))
            }
            bos.flush()
            bos.close()
            // connection headers
            val url = Uri.parse(syncURL()).buildUpon().appendPath(method).toString()
            val requestBuilder = Request.Builder()
            requestBuilder.url(parseUrl(url))

            // Set our request up to count upstream traffic including headers
            requestBuilder.post(
                CountingFileRequestBody(
                    tmpFileBuffer,
                    ANKI_POST_TYPE.toString(),
                    object : CountingFileRequestBody.ProgressListener {
                        override fun transferred(num: Long) {
                            bytesSent.addAndGet(num)
                            publishProgress()
                        }
                    }
                )
            )
            val httpPost: Request = requestBuilder.build()
            bytesSent.addAndGet(httpPost.headers.byteCount())
            publishProgress()
            try {
                val httpClient = httpClient
                val httpResponse = httpClient.newCall(httpPost).execute()

                // we assume badAuthRaises flag from Anki Desktop always False
                // so just throw new RuntimeException if response code not 200 or 403
                Timber.d(
                    "TLSVersion in use is: %s",
                    if (httpResponse.handshake != null) httpResponse.handshake!!.tlsVersion else "unknown"
                )

                // Count downstream traffic including headers
                bytesReceived.addAndGet(httpResponse.headers.byteCount())
                try {
                    bytesReceived.addAndGet(httpResponse.body!!.contentLength())
                } catch (npe: NullPointerException) {
                    Timber.d(npe, "Unexpected null response body")
                }
                publishProgress()
                assertOk(httpResponse)
                httpResponse
            } catch (e: SSLException) {
                Timber.e(e, "SSLException while building HttpClient")
                throw RuntimeException("SSLException while building HttpClient", e)
            }
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            Timber.e(e, "BasicHttpSyncer.sync: IOException")
            if (e is UnknownHostException) {
                throw e
            } else {
                throw RuntimeException(e)
            }
        } finally {
            if (tmpFileBuffer != null && tmpFileBuffer.exists()) {
                tmpFileBuffer.delete()
            }
        }
    }

    private fun parseUrl(url: String): HttpUrl {
        // #5843 - show better exception if the URL is invalid
        return try {
            url.toHttpUrl()
        } catch (ex: IllegalArgumentException) {
            if (getCustomSyncUrlOrNull() != null) {
                throw CustomSyncServerUrlException(url, ex)
            } else {
                throw ex
            }
        }
    }

    // Could be replaced by Compat copy method if that method took listener for bytesReceived/publishProgress()
    @Throws(IOException::class)
    fun writeToFile(source: InputStream, destination: String?) {
        val file = File(destination!!)
        var output: OutputStream? = null
        try {
            file.createNewFile()
            output = BufferedOutputStream(FileOutputStream(file))
            val buf = ByteArray(Utils.CHUNK_SIZE)
            var len: Int
            while (source.read(buf).also { len = it } >= 0) {
                output.write(buf, 0, len)
                bytesReceived.addAndGet(len.toLong())
                publishProgress()
            }
        } catch (e: IOException) {
            if (file.exists()) {
                // Don't keep the file if something went wrong. It'll be corrupt.
                file.delete()
            }
            throw e
        } finally {
            output?.close()
        }
    }

    fun stream2String(stream: InputStream?, maxSize: Int): String {
        val rd: BufferedReader
        return try {
            rd = BufferedReader(
                InputStreamReader(stream, "UTF-8"),
                if (maxSize == -1) 4096 else min(4096, maxSize)
            )
            var line: String
            val sb = StringBuilder()
            while (rd.readLine()
                .also { line = it } != null && (maxSize == -1 || sb.length < maxSize)
            ) {
                sb.append(line)
                bytesReceived.addAndGet(line.length.toLong())
                publishProgress()
            }
            rd.close()
            sb.toString()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun publishProgress() {
        Timber.d("Publishing progress")
        if (con != null && (nextSendR <= bytesReceived.get() || nextSendS <= bytesSent.get())) {
            val bR = bytesReceived.get()
            val bS = bytesSent.get()
            Timber.d("Current progress: %d, %d", bR, bS)
            nextSendR = (bR / 1024 + 1) * 1024
            nextSendS = (bS / 1024 + 1) * 1024
            con.publishProgress(0, bS, bR)
        }
    }

    val preferences: SharedPreferences get() = AnkiDroidApp.instance.sharedPrefs()

    open fun getDefaultSyncUrl() = "https://sync${hostNum ?: ""}.ankiweb.net/sync/"

    open fun getCustomSyncUrlOrNull() =
        CustomSyncServer.getCollectionSyncUrlIfSetAndEnabledOrNull(preferences)

    fun syncURL() = getCustomSyncUrlOrNull() ?: getDefaultSyncUrl()

    protected val hostNum: Int?
        get() = mHostNum.hostNum

    companion object {
        private const val BOUNDARY = "Anki-sync-boundary"
        private val ANKI_POST_TYPE: MediaType =
            ("multipart/form-data; boundary=$BOUNDARY").toMediaType()
        const val ANKIWEB_STATUS_OK = "OK"
        fun getInputStream(string: String): ByteArrayInputStream? {
            return try {
                ByteArrayInputStream(string.toByteArray(charset("UTF-8")))
            } catch (e: UnsupportedEncodingException) {
                Timber.e(e, "HttpSyncer: error on getting bytes from string")
                null
            }
        }
    }

    init {
        @KotlinCleanup("combined declaration and initialization")
        checksumKey = Utils.checksum(Random().nextFloat().toString()).substring(0, 8)
        @KotlinCleanup("move to constructor")
        this.con = con
        @KotlinCleanup("combined declaration and initialization")
        postVars =
            HashMapInit(0) // New map is created each time it is filled. No need to allocate room
        @KotlinCleanup("move to constructor")
        mHostNum = hostNum
    }
}
