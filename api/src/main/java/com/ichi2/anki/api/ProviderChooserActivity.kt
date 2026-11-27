// SPDX-FileCopyrightText: Copyright (c) 2026 hill <hill@hilll.dev>
// SPDX-License-Identifier: LGPL-3.0-or-later
// The API library doesn't depend on AppCompat
@file:Suppress("AvoidAlertDialogUsage")

package com.ichi2.anki.api

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import com.ichi2.anki.api.ProviderChooser.State

/**
 * Lets the user pick which installed [FlashCardsProvider] this app talks to.
 * Picking one asks for its permission through Android's permission dialog.
 *
 * Declared in the library's manifest, so client apps get it without changes.
 */
internal class ProviderChooserActivity : Activity() {
    private lateinit var selection: ProviderSelection
    private lateinit var chooser: ProviderChooser
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selection = ProviderSelection(this)

        // Nothing to choose from: close
        val providers = selection.installed()
        if (providers.isEmpty()) {
            finish()
            return
        }

        val awaitingPermission =
            savedInstanceState?.let { state ->
                val packageName = state.getString(STATE_PENDING_PACKAGE)
                val authority = state.getString(STATE_PENDING_AUTHORITY)
                providers.firstOrNull { it.packageName == packageName && it.authority == authority }
            }
        chooser = ProviderChooser(providers, selection.inUse(), awaitingPermission) { permissionState(it) }
        if (chooser.state is State.Choosing) {
            showList()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::chooser.isInitialized) {
            return
        }
        val awaiting = chooser.state as? State.AwaitingPermission ?: return
        outState.putString(STATE_PENDING_PACKAGE, awaiting.provider.packageName)
        outState.putString(STATE_PENDING_AUTHORITY, awaiting.provider.authority)
    }

    override fun onDestroy() {
        dialog?.dismiss()
        super.onDestroy()
    }

    /** Shows the providers by app name and icon, the one in use checked */
    private fun showList() {
        val selected = (chooser.state as? State.Choosing)?.selected
        // An app with AnkiDroid's package name that fails the certificate check is marked
        val unverified = chooser.providers.filter { it.packageName == BuildConfig.ANKI_PACKAGE_NAME && !selection.isAnkiDroid(it) }
        val adapter = ProviderAdapter(this, chooser.providers, selected, unverified.toSet())

        dialog =
            AlertDialog
                .Builder(this)
                .setTitle(R.string.ankidroid_api_provider_chooser_title)
                .setAdapter(adapter) { _, which -> render(chooser.pick(chooser.providers[which])) }
                .setOnCancelListener { render(chooser.cancel()) }
                .show()
    }

    /** Acts on the chooser's new state */
    private fun render(state: State) {
        when (state) {
            is State.Choosing -> showList()
            is State.AwaitingPermission -> requestPermission(state.provider)
            is State.Chosen -> {
                if (selection.choose(state.provider)) {
                    setResult(RESULT_OK)
                }
                finish()
            }
            State.Cancelled -> finish()
        }
    }

    // Before Android 6 permissions are granted at install time, so a missing one stays denied
    private fun requestPermission(provider: FlashCardsProvider) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            render(chooser.onPermission(PermissionState.DENIED))
            return
        }
        requestPermissions(arrayOf(provider.permission), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST || !::chooser.isInitialized) {
            return
        }

        val awaiting = chooser.state as? State.AwaitingPermission ?: return
        val granted =
            permissions.singleOrNull() == awaiting.provider.permission &&
                grantResults.singleOrNull() == PackageManager.PERMISSION_GRANTED
        render(chooser.onPermission(if (granted) PermissionState.GRANTED else PermissionState.DENIED))
    }

    private fun permissionState(permission: String): PermissionState {
        val result = checkPermission(permission, Process.myPid(), Process.myUid())
        return if (result == PackageManager.PERMISSION_GRANTED) PermissionState.GRANTED else PermissionState.DENIED
    }

    /** One row per provider: the app's name and icon, checked if in use */
    private class ProviderAdapter(
        context: Context,
        providers: List<FlashCardsProvider>,
        private val selected: FlashCardsProvider?,
        private val unverified: Set<FlashCardsProvider>,
    ) : ArrayAdapter<FlashCardsProvider>(context, android.R.layout.select_dialog_singlechoice, providers) {
        private val manager = context.packageManager
        private val iconSize = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val view = super.getView(position, convertView, parent) as CheckedTextView
            val provider = getItem(position) ?: return view

            // A provider uninstalled while the list is open keeps its package name
            val app =
                try {
                    manager.getApplicationInfo(provider.packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            // The package name tells apart apps with the same name
            val label = app?.loadLabel(manager) ?: provider.packageName
            val details =
                if (provider in unverified) {
                    context.getString(R.string.ankidroid_api_provider_unverified, provider.packageName)
                } else {
                    provider.packageName
                }
            view.text = "$label\n$details"
            val icon = app?.loadIcon(manager)?.apply { setBounds(0, 0, iconSize, iconSize) }
            view.setCompoundDrawables(icon, null, null, null)
            view.compoundDrawablePadding = iconSize / 2
            view.isChecked = provider == selected
            return view
        }
    }

    private companion object {
        const val PERMISSION_REQUEST = 1
        const val ICON_SIZE_DP = 32
        const val STATE_PENDING_PACKAGE = "pendingPackage"
        const val STATE_PENDING_AUTHORITY = "pendingAuthority"
    }
}
