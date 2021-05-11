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

package com.ichi2.libanki.sync;

import android.os.Build;

import com.ichi2.anki.AnkiDroidApp;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import timber.log.Timber;

/**
 * Enables TLS v1.2 when creating SSLSockets.
 * <p/>
 * This hack is currently only maintained with API >= 21 for some Samsung API21 phones
 *
 * @link https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
 * @see SSLSocketFactory
 */
public class Tls12SocketFactory extends SSLSocketFactory {
    private static final String[] TLS_V12_ONLY =  {"TLSv1.2"};

    private final SSLSocketFactory mDelegate;


    public static OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 && "Samsung".equals(Build.MANUFACTURER)) {
            try {
                Timber.d("Creating unified TrustManager");
                Certificate cert = getUserTrustRootCertificate();

                String keyStoreType = KeyStore.getDefaultType();
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", cert);
                UnifiedTrustManager trustManager = new UnifiedTrustManager(keyStore);
                Timber.d("Finished: Creating unified TrustManager");

                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, new TrustManager[] {trustManager}, null);
                Tls12SocketFactory socketFactory = new Tls12SocketFactory(sc.getSocketFactory());
                client.sslSocketFactory(socketFactory, trustManager);

                ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .build();

                List<ConnectionSpec> specs = new ArrayList<>(3);
                specs.add(cs);
                specs.add(ConnectionSpec.COMPATIBLE_TLS);
                specs.add(ConnectionSpec.CLEARTEXT);

                client.connectionSpecs(specs);
            } catch (Exception exc) {
                Timber.e(exc, "Error while setting TLS 1.2");
            }
        }

        return client;
    }


    private static Certificate getUserTrustRootCertificate() throws CertificateException, IOException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        try (InputStream crt = AnkiDroidApp.getResourceAsStream("assets/USERTrust_RSA.crt")) {
            return cf.generateCertificate(crt);
        }
    }


    private Tls12SocketFactory(SSLSocketFactory base) {
        this.mDelegate = base;
    }


    @Override
    public String[] getDefaultCipherSuites() {
        return mDelegate.getDefaultCipherSuites();
    }


    @Override
    public String[] getSupportedCipherSuites() {
        return mDelegate.getSupportedCipherSuites();
    }


    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return patch(mDelegate.createSocket(s, host, port, autoClose));
    }


    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return patch(mDelegate.createSocket(host, port));
    }


    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return patch(mDelegate.createSocket(host, port, localHost, localPort));
    }


    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return patch(mDelegate.createSocket(host, port));
    }


    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return patch(mDelegate.createSocket(address, port, localAddress, localPort));
    }


    private Socket patch(Socket s) {
        if (s instanceof SSLSocket) {
            ((SSLSocket) s).setEnabledProtocols(TLS_V12_ONLY);
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

        return s;
    }
}