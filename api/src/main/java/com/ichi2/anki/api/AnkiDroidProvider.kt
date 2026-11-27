// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import com.ichi2.anki.FlashCardsContract
import java.security.MessageDigest
import java.util.Locale

/**
 * Finds the app behind [FlashCardsContract.AUTHORITY] and checks that it is AnkiDroid.
 *
 * Any app can declare the authority while AnkiDroid is not installed, and would then receive
 * the notes that clients add. The provider is trusted only if its package is AnkiDroid's and
 * it is signed with one of AnkiDroid's release keys.
 *
 * Self-built and debug builds of AnkiDroid are signed with other keys. They are trusted only
 * while the calling app is debuggable, so a released client never accepts them.
 */
internal object AnkiDroidProvider {
    private const val TAG = "AnkiDroidApi"
    private const val DIGEST_ALGORITHM = "SHA-256"

    /**
     * SHA-256 digests of the certificates AnkiDroid's releases are signed with.
     *
     * Read from the APK signing block of the releases on
     * https://github.com/ankidroid/Anki-Android/releases (play and full flavors) and of
     * com.ichi2.anki_225033004.apk on https://f-droid.org.
     */
    private val RELEASE_CERTIFICATES =
        setOf(
            // Google Play and GitHub releases: CN=Nicolas Raoul, O=http://nrw.free.fr
            "2071534F0F4B5E54AE952DD275D70DA6E3459EE69909D2AB1B4843C4C5B21A45",
            // F-Droid builds AnkiDroid itself and signs it with its own key for this app
            "24A3D4756C62DD69C86DF69AA7FA327B2A6BF55F55C4A1749F99BD1DA75906BB",
        )

    /** Outcome of checking the app that holds [FlashCardsContract.AUTHORITY] */
    private enum class Trust(
        val trusted: Boolean,
    ) {
        /** AnkiDroid, signed with a release key */
        GENUINE(trusted = true),

        /** AnkiDroid's package with another key, accepted because the calling app is debuggable */
        DEBUG_BUILD(trusted = true),

        /** Another package holds the authority */
        WRONG_PACKAGE(trusted = false),

        /** AnkiDroid's package, not signed with a release key */
        UNKNOWN_CERTIFICATE(trusted = false),
    }

    /**
     * The provider behind [FlashCardsContract.AUTHORITY] if its app is AnkiDroid.
     * @param flags [PackageManager] flags for resolving the provider, such as [PackageManager.GET_META_DATA]
     * @return null if AnkiDroid is not installed, or if another app holds the authority
     */
    fun resolve(
        context: Context,
        flags: Int = 0,
    ): ProviderInfo? {
        val provider = find(context.packageManager, flags) ?: return null
        if (!check(context, provider).trusted) {
            return null
        }
        return provider
    }

    /** Checks the provider's package name and certificate, and logs why it is not trusted */
    private fun check(
        context: Context,
        provider: ProviderInfo,
    ): Trust {
        val trust = trustOf(context, provider.packageName)

        // Say why AnkiDroid counts as not installed, so developers can find the cause
        when (trust) {
            Trust.GENUINE -> {}
            Trust.DEBUG_BUILD -> Log.i(TAG, "Accepting ${provider.packageName} without a release key: this app is debuggable")
            Trust.WRONG_PACKAGE ->
                Log.w(TAG, "${provider.packageName} holds ${FlashCardsContract.AUTHORITY}: treating AnkiDroid as not installed")
            Trust.UNKNOWN_CERTIFICATE ->
                Log.w(TAG, "${provider.packageName}'s signing identity is unrecognized: treating AnkiDroid as not installed")
        }
        return trust
    }

    private fun trustOf(
        context: Context,
        packageName: String,
    ): Trust {
        // Only AnkiDroid's package name can hold AnkiDroid's authority
        if (packageName != BuildConfig.ANKI_PACKAGE_NAME) {
            return Trust.WRONG_PACKAGE
        }

        if (hasReleaseCertificate(context.packageManager, packageName)) {
            return Trust.GENUINE
        }

        // Developers of a debuggable client may test against their own AnkiDroid build
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return Trust.DEBUG_BUILD
        }
        return Trust.UNKNOWN_CERTIFICATE
    }

    @SuppressLint("WrongConstant") // ComponentInfoFlags bug: GET_META_DATA.toLong() was invalid
    private fun find(
        manager: PackageManager,
        flags: Int,
    ): ProviderInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return manager.resolveContentProvider(
                FlashCardsContract.AUTHORITY,
                PackageManager.ComponentInfoFlags.of(flags.toLong()),
            )
        }
        return manager.resolveContentProvider(FlashCardsContract.AUTHORITY, flags)
    }

    /** Official releases have one signer. A rotated key can match through its verified history. */
    @Suppress("DEPRECATION") // GET_SIGNATURES and signatures, used only before API 28
    private fun hasReleaseCertificate(
        manager: PackageManager,
        packageName: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val signer =
                packageInfo(manager, packageName, PackageManager.GET_SIGNATURES)?.signatures?.singleOrNull()
                    ?: return false
            return digest(signer) in RELEASE_CERTIFICATES
        }

        val info = packageInfo(manager, packageName, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo ?: return false
        // For multiple signers, Android treats the full set as the identity. No release uses such a set.
        if (info.hasMultipleSigners()) {
            return false
        }
        return info.signingCertificateHistory?.any { digest(it) in RELEASE_CERTIFICATES } == true
    }

    @SuppressLint("WrongConstant") // as in find()
    @Suppress("DEPRECATION") // getPackageInfo(String, Int), used only before API 33
    private fun packageInfo(
        manager: PackageManager,
        packageName: String,
        flags: Int,
    ): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                manager.getPackageInfo(packageName, flags)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "$packageName holds a provider but has no package info", e)
            null
        }

    /** Upper-case hex SHA-256 of the certificate, as `apksigner` and `keytool` print it */
    private fun digest(certificate: Signature): String =
        MessageDigest
            .getInstance(DIGEST_ALGORITHM)
            .digest(certificate.toByteArray())
            .joinToString("") { "%02X".format(Locale.US, it) }
}
