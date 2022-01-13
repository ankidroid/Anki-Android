/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.util.DialogUtils
import com.ichi2.anki.*
import com.ichi2.anki.cardviewer.CardAppearance
import com.ichi2.ui.FixedTextView
import timber.log.Timber

typealias OpenUri = (Uri) -> Unit

/**
 * Informs the user that a scoped storage migration will occur
 *
 * Explains risks of not migrating
 * Explains time taken to migrate
 * Obtains consent
 *
 * For info, see: docs\scoped_storage\README.md
 * For design decisions, see: docs\scoped_storage\consent.md
 */
object ScopedStorageMigrationDialog {
    @JvmStatic
    fun showDialog(ctx: Context, openUri: OpenUri): Dialog {
        return MaterialDialog.Builder(ctx)
            .title(R.string.scoped_storage_title)
            .content(R.string.scoped_storage_initial_message)
            .positiveText(R.string.scoped_storage_migrate)
            .onPositive { dialog, _ ->
                run {
                    ScopedStorageMigrationConfirmationDialog.showDialog(ctx)
                    dialog.dismiss()
                }
            }
            .neutralText(R.string.scoped_storage_learn_more)
            .onNeutral { _, _ -> openMoreInfo(ctx, openUri) }
            .negativeText(R.string.scoped_storage_postpone)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .cancelable(false)
            .autoDismiss(false)
            .show()
    }
}

/**
 * Obtains explicit consent that:
 *
 * * User will not uninstall the app
 * * User has either
 *   * performed an AnkiWeb sync (if an account is found)
 *   * performed a regular backup
 *
 * Then performs a migration to scoped storage
 */
object ScopedStorageMigrationConfirmationDialog {
    fun showDialog(ctx: Context): Dialog {
        val li = LayoutInflater.from(ctx)
        val view = li.inflate(R.layout.scoped_storage_confirmation, null)

        // scoped_storage_terms_message requires a format arg: estimated time taken
        val textView = view.findViewById<FixedTextView>(R.id.scoped_storage_content)
        textView.text = ctx.getString(R.string.scoped_storage_terms_message, "??? minutes")

        val userWillNotUninstall = view.findViewById<CheckBox>(R.id.scoped_storage_no_uninstall)
        val noAnkiWeb = view.findViewById<CheckBox>(R.id.scoped_storage_no_ankiweb)
        val ifAnkiWeb = view.findViewById<CheckBox>(R.id.scoped_storage_ankiweb)

        // If the user has an AnkiWeb account, ask them to sync, otherwise ask them to backup
        val hasAnkiWebAccount = Preferences.hasAnkiWebAccount(AnkiDroidApp.getSharedPrefs(ctx))

        val backupMethodToDisable = if (hasAnkiWebAccount) noAnkiWeb else ifAnkiWeb
        val backupMethodToUse = if (hasAnkiWebAccount) ifAnkiWeb else noAnkiWeb

        backupMethodToDisable.visibility = View.GONE

        // hack: should be performed in custom_material_dialog_content style
        getContentColor(ctx)?.let {
            textView.setTextColor(it)
            userWillNotUninstall.setTextColor(it)
            noAnkiWeb.setTextColor(it)
            ifAnkiWeb.setTextColor(it)
        }

        val checkboxesRequiredToContinue = listOf(userWillNotUninstall, backupMethodToUse)

        return MaterialDialog.Builder(ctx)
            .title(R.string.scoped_storage_title)
            .customView(view, true)
            .positiveText(R.string.scoped_storage_migrate)
            .onPositive { dialog, _ ->
                if (checkboxesRequiredToContinue.all { x -> x.isChecked }) {
                    Timber.d("enable scoped storage migration")
                    dialog.dismiss()
                } else {
                    UIUtils.showThemedToast(ctx, R.string.scoped_storage_select_all_terms, true)
                }
            }
            .negativeText(R.string.scoped_storage_postpone)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .cancelable(false)
            .autoDismiss(false)
            .show()
    }

    private fun getContentColor(ctx: Context): Int? {
        return try {
            val isDarkTheme = CardAppearance.isInNightMode(AnkiDroidApp.getSharedPrefs(ctx))

            val theme = if (isDarkTheme) com.afollestad.materialdialogs.R.style.MD_Dark else com.afollestad.materialdialogs.R.style.MD_Light

            val contextThemeWrapper = ContextThemeWrapper(ctx, theme)
            val contentColorFallback = DialogUtils.resolveColor(contextThemeWrapper, android.R.attr.textColorSecondary)
            DialogUtils.resolveColor(contextThemeWrapper, com.afollestad.materialdialogs.R.attr.md_content_color, contentColorFallback)
        } catch (e: Exception) {
            null
        }
    }
}

/** Opens more info about scoped storage (AnkiDroid wiki) */
private fun openMoreInfo(ctx: Context, openUri: (Uri) -> (Unit)) {
    val faqUri = Uri.parse(ctx.getString(R.string.link_scoped_storage_faq))
    openUri(faqUri)
}

fun openUrl(activity: AnkiActivity): OpenUri = activity::openUrl
