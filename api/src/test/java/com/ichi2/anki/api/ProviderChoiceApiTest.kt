// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.ichi2.anki.FlashCardsContract
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records the URIs it is queried with */
internal class RecordingProvider : ContentProvider() {
    val queried = mutableListOf<Uri>()

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        queried += uri
        return null
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

/** [AddContentApi] with a fork offering the same provider */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S_V2])
internal class ProviderChoiceApiTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        // A released client which requests the fork's permission
        val info = context.applicationInfo
        info.flags = info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        AnkiDroidPackages.request(FlashCardsContract.READ_WRITE_PERMISSION, FORK.permission)
    }

    @Test
    fun forkIsUnusedUntilChosen() {
        installFork()
        val recorder = recorder()

        assertEquals(listOf(FORK), AddContentApi.getProviders(context))
        assertNull(AddContentApi.getAnkiDroidPackageName(context))
        assertNull(AddContentApi.getSelectedProvider(context))
        assertEquals(-1, AddContentApi(context).apiHostSpecVersion)
        assertNull(AddContentApi(context).deckList)
        assertEquals(emptyList(), recorder.queried)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun chosenForkIsUsed() {
        installFork()
        AnkiDroidPackages.install(BuildConfig.ANKI_PACKAGE_NAME, Signing.Current(AnkiDroidPackages.RELEASE), declaresInterface = true)
        val recorder = recorder()

        assertTrue(ProviderSelection(context).choose(FORK))

        assertEquals(BuildConfig.ANKI_PACKAGE_NAME, AddContentApi.getAnkiDroidPackageName(context))
        assertEquals(FORK, AddContentApi.getSelectedProvider(context))
        assertEquals(FORK_SPEC, AddContentApi(context).apiHostSpecVersion)

        // Every URI is built from the fork's authority
        assertNull(AddContentApi(context).deckList)
        assertEquals(listOf(FORK.authority), recorder.queried.map { it.authority })
    }

    @Test
    fun legacyLookupDoesNotValidateUnverifiedStockWhenAForkIsSelected() {
        installFork()
        AnkiDroidPackages.install(BuildConfig.ANKI_PACKAGE_NAME, Signing.Current(FORK_CERTIFICATE))
        assertTrue(ProviderSelection(context).choose(FORK))

        assertNull(AddContentApi.getAnkiDroidPackageName(context))
        assertEquals(FORK, AddContentApi.getSelectedProvider(context))
    }

    @Test
    fun chooserIntentDoesNotLaunchAnActivity() {
        val intent = AddContentApi.getProviderChooserIntent(context)

        assertEquals(ProviderChooserActivity::class.java.name, intent.component?.className)
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun chooserReturnsSavedProviderAfterPermissionAndRecreation() {
        installFork()
        val intent = AddContentApi.getProviderChooserIntent(context)
        val controller = Robolectric.buildActivity(ProviderChooserActivity::class.java, intent).setup()
        val list = assertNotNull(ShadowAlertDialog.getLatestAlertDialog()).listView
        assertTrue(list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0)))
        val request = assertNotNull(shadowOf(controller.get()).lastRequestedPermission)
        assertEquals(listOf(FORK.permission), request.requestedPermissions.toList())
        assertNull(AddContentApi.getSelectedProvider(context))

        val recreated = controller.recreate().get()
        shadowOf(context).grantPermissions(FORK.permission)
        recreated.onRequestPermissionsResult(
            request.requestCode,
            request.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_GRANTED),
        )

        assertEquals(Activity.RESULT_OK, shadowOf(recreated).resultCode)
        assertTrue(recreated.isFinishing)
        assertEquals(FORK, AddContentApi.getSelectedProvider(context))
    }

    private fun installFork() {
        AnkiDroidPackages.install(
            FORK.packageName,
            Signing.Current(FORK_CERTIFICATE),
            Bundle().apply { putInt("com.ichi2.anki.provider.spec", FORK_SPEC) },
            authority = FORK.authority,
            declaresInterface = true,
        )
    }

    private fun recorder(): RecordingProvider =
        Robolectric
            .buildContentProvider(RecordingProvider::class.java)
            .create(FORK.authority)
            .get()

    private companion object {
        const val FORK_SPEC = 3
        val FORK = FlashCardsProvider("org.example.ankifork", "org.example.ankifork.flashcards")
        val FORK_CERTIFICATE = Signature(byteArrayOf(1, 2, 3))
    }
}
