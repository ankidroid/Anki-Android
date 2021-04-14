/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki.sync;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import timber.log.Timber;

// https://stackoverflow.com/questions/27562666/programmatically-add-a-certificate-authority-while-keeping-android-system-ssl-ce
// Changes:
// We try the local manager first.
// Cached the accepted issuers.
// Did not ignore NoSuchAlgorithmException
class UnifiedTrustManager implements X509TrustManager {
    private X509TrustManager mDefaultTrustManager;
    private X509TrustManager mLocalTrustManager;
    private X509Certificate[] mAcceptedIssuers;

    public UnifiedTrustManager(KeyStore localKeyStore) throws KeyStoreException, NoSuchAlgorithmException {
        this.mDefaultTrustManager = createTrustManager(null);
        this.mLocalTrustManager = createTrustManager(localKeyStore);
        X509Certificate[] first = mDefaultTrustManager.getAcceptedIssuers();
        X509Certificate[] second = mLocalTrustManager.getAcceptedIssuers();
        mAcceptedIssuers = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, mAcceptedIssuers, first.length, second.length);
    }

    private X509TrustManager createTrustManager(KeyStore store) throws NoSuchAlgorithmException, KeyStoreException {
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(store);
        TrustManager[] trustManagers = tmf.getTrustManagers();
        return (X509TrustManager) trustManagers[0];
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            mLocalTrustManager.checkServerTrusted(chain, authType);
        } catch (CertificateException ce) {
            Timber.w(ce);
            mDefaultTrustManager.checkServerTrusted(chain, authType);
        }
    }
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            mLocalTrustManager.checkClientTrusted(chain, authType);
        } catch (CertificateException ce) {
            Timber.w(ce);
            mDefaultTrustManager.checkClientTrusted(chain, authType);
        }
    }
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return mAcceptedIssuers;
    }
}
