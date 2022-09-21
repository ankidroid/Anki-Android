/****************************************************************************************
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

import android.os.Build
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.utils.KotlinCleanup
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager

/**
 * Enables TLS v1.2 when creating SSLSockets.
 *
 *
 * This hack is currently only maintained with API >= 21 for some Samsung API21 phones
 *
 * @link https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
 * @see SSLSocketFactory
 */
class Tls12SocketFactory private constructor(
    private val delegate: SSLSocketFactory
) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> {
        return delegate.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        return patch(delegate.createSocket(s, host, port, autoClose))
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket {
        return patch(delegate.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        return patch(delegate.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket {
        return patch(delegate.createSocket(address, port, localAddress, localPort))
    }

    private fun patch(s: Socket): Socket {
        if (s is SSLSocket) {
            s.enabledProtocols = TLS_V12_ONLY
        }

        // Note if progress tracking needs to be more granular than default OkHTTP buffer, do this:
//        try {
//            s.setSendBufferSize(16 * 1024);
//            // We will only know if this is a problem if people complain about progress bar going to 100%
//            // on small transfers (indicating buffer ate all contents) before transfer finishes (because buffer is still flushing)
//            // Important to say that this can slow things down dramatically though so needs tuning. With 16kb throughput was 40kb/s
//            // By default throughput was maxing my 50Mbit line out (!)
//        } catch (SocketException se) {
//            Timber.e(se, "Unable to set socket send buffer size");
//        }
        return s
    }

    companion object {
        private val TLS_V12_ONLY = arrayOf("TLSv1.2")

        fun enableTls12OnPreLollipop(client: OkHttpClient.Builder): OkHttpClient.Builder {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 && "Samsung" == Build.MANUFACTURER) {
                try {
                    Timber.d("Creating unified TrustManager")
                    val cert = userTrustRootCertificate

                    val keyStoreType = KeyStore.getDefaultType()
                    val keyStore = KeyStore.getInstance(keyStoreType)
                    keyStore.load(null, null)
                    keyStore.setCertificateEntry("ca", cert)
                    val trustManager = UnifiedTrustManager(keyStore)
                    Timber.d("Finished: Creating unified TrustManager")

                    val sc = SSLContext.getInstance("TLSv1.2")
                    sc.init(null, arrayOf<TrustManager>(trustManager), null)
                    val socketFactory = Tls12SocketFactory(sc.socketFactory)
                    client.sslSocketFactory(socketFactory, trustManager)

                    val cs: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build()

                    val specs: MutableList<ConnectionSpec> = ArrayList(3)
                    specs.add(cs)
                    specs.add(ConnectionSpec.COMPATIBLE_TLS)
                    specs.add(ConnectionSpec.CLEARTEXT)
                    client.connectionSpecs(specs)
                } catch (exc: Exception) {
                    Timber.e(exc, "Error while setting TLS 1.2")
                }
            }
            return client
        }

        @get:Throws(CertificateException::class, IOException::class)
        @KotlinCleanup("has one usage inside this class, try to inline this property")
        private val userTrustRootCertificate: Certificate
            get() {
                val cf = CertificateFactory.getInstance("X.509")
                AnkiDroidApp.getResourceAsStream("assets/USERTrust_RSA.crt")
                    .use { crt -> return cf.generateCertificate(crt) }
            }
    }
}
