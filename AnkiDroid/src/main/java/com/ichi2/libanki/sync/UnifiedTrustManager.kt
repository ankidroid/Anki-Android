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
package com.ichi2.libanki.sync

import timber.log.Timber
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

// https://stackoverflow.com/questions/27562666/programmatically-add-a-certificate-authority-while-keeping-android-system-ssl-ce
// Changes:
// We try the local manager first.
// Cached the accepted issuers.
// Did not ignore NoSuchAlgorithmException
internal class UnifiedTrustManager(localKeyStore: KeyStore?) : X509TrustManager {
    private val mDefaultTrustManager: X509TrustManager
    private val mLocalTrustManager: X509TrustManager
    private val mAcceptedIssuers: Array<X509Certificate>

    @Throws(NoSuchAlgorithmException::class, KeyStoreException::class)
    private fun createTrustManager(store: KeyStore?): X509TrustManager {
        val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
        tmf.init(store)
        val trustManagers = tmf.trustManagers
        return trustManagers[0] as X509TrustManager
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            mLocalTrustManager.checkServerTrusted(chain, authType)
        } catch (ce: CertificateException) {
            Timber.w(ce)
            mDefaultTrustManager.checkServerTrusted(chain, authType)
        }
    }

    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            mLocalTrustManager.checkClientTrusted(chain, authType)
        } catch (ce: CertificateException) {
            Timber.w(ce)
            mDefaultTrustManager.checkClientTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return mAcceptedIssuers
    }

    init {
        mDefaultTrustManager = createTrustManager(null)
        mLocalTrustManager = createTrustManager(localKeyStore)
        val first = mDefaultTrustManager.acceptedIssuers
        val second = mLocalTrustManager.acceptedIssuers
        mAcceptedIssuers = Arrays.copyOf(first, first.size + second.size)
        System.arraycopy(second, 0, mAcceptedIssuers, first.size, second.size)
    }
}
