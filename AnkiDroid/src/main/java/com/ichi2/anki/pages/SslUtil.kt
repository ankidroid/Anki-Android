// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 mixelas <michelakisgio@gmail.com>

package com.ichi2.anki.pages

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

/** Manages SSL/TLS support for HTTPS connections in AnkiServer */
object SslUtil {
    private const val KEYSTORE_TYPE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS_PREFIX = "ankidroid-localhost"
    private const val KEY_SIZE = 2048
    private const val CERT_VALIDITY_MS = 3650L * 24 * 60 * 60 * 1000 // 10 years

    /**
     * Get or create an SSLContext for localhost HTTPS using AndroidKeyStore.
     *
     * The alias is derived from cacheDir path so each profile gets a stable keypair.
     */
    fun getSSLContext(cacheDir: File): SSLContext {
        val alias = "$KEYSTORE_ALIAS_PREFIX-${cacheDir.absolutePath.hashCode().toUInt().toString(16)}"
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }

        if (!keyStore.containsAlias(alias)) {
            generateKeyPair(alias)
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, null)

        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, null, SecureRandom())
        }
    }

    @Suppress("DirectDateInstantiation", "DirectSystemCurrentTimeMillisUsage")
    private fun generateKeyPair(alias: String) {
        try {
            val now = System.currentTimeMillis()
            val spec =
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(KEY_SIZE)
                    .setCertificateSubject(X500Principal("CN=localhost"))
                    .setCertificateSerialNumber(BigInteger.valueOf(now))
                    .setCertificateNotBefore(Date(now - 60_000L))
                    .setCertificateNotAfter(Date(now + CERT_VALIDITY_MS))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build()

            val keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_TYPE)
            keyGen.initialize(spec)
            keyGen.generateKeyPair()
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate AndroidKeyStore certificate for localhost HTTPS")
            throw e
        }
    }
}
