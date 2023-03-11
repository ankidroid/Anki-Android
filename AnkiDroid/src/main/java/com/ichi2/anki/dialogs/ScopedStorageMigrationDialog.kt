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

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.core.text.parseAsHtml
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.utils.MDUtil
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.preferences.Preferences
import com.ichi2.themes.Themes
import com.ichi2.ui.FixedTextView
import makeLinksClickable
import org.intellij.lang.annotations.Language
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
    @Suppress("Deprecation") // Material dialog neutral button deprecation
    @SuppressLint("CheckResult")
    fun showDialog(ctx: Context, openUri: OpenUri, initiateScopedStorage: Runnable): Dialog {
        return MaterialDialog(ctx).show {
            title(R.string.scoped_storage_title)
            message(R.string.scoped_storage_initial_message)
            positiveButton(R.string.scoped_storage_migrate) {
                run {
                    ScopedStorageMigrationConfirmationDialog.showDialog(ctx, initiateScopedStorage)
                    dismiss()
                }
            }
            neutralButton(R.string.scoped_storage_learn_more) {
                // TODO: Discuss regarding alternatives to using a neutral button here
                //  since it is deprecated and not recommended in material guidelines
                openMoreInfo(ctx, openUri)
            }
            negativeButton(R.string.scoped_storage_postpone) {
                dismiss()
            }
            cancelable(false)
            noAutoDismiss()
        }
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
    @SuppressLint("CheckResult")
    fun showDialog(ctx: Context, initiateScopedStorage: Runnable): Dialog {
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

        return MaterialDialog(ctx).show {
            customView(view = view, scrollable = true)
            title(R.string.scoped_storage_title)
            positiveButton(R.string.scoped_storage_migrate) {
                if (checkboxesRequiredToContinue.all { x -> x.isChecked }) {
                    Timber.i("starting scoped storage migration")
                    dismiss()
                    initiateScopedStorage.run()
                } else {
                    UIUtils.showThemedToast(ctx, R.string.scoped_storage_select_all_terms, true)
                }
            }
            negativeButton(R.string.scoped_storage_postpone) {
                dismiss()
            }
            cancelable(false)
            noAutoDismiss()
        }
    }

    private fun getContentColor(ctx: Context): Int? {
        return try {
            val theme = if (Themes.currentTheme.isNightMode) com.afollestad.materialdialogs.R.style.MD_Dark else com.afollestad.materialdialogs.R.style.MD_Light

            val contextThemeWrapper = ContextThemeWrapper(ctx, theme)
            val contentColorFallback = MDUtil.resolveColor(contextThemeWrapper, android.R.attr.textColorSecondary)
            MDUtil.resolveColor(contextThemeWrapper, com.afollestad.materialdialogs.R.attr.md_color_content, contentColorFallback)
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

/**
 * Set [message] as the dialog message, followed by "Learn More", as a link to Scoped Storage faq.
 * Then show the dialog.
 * @return the dialog
 */
fun AlertDialog.Builder.addScopedStorageLearnMoreLinkAndShow(@Language("HTML") message: String): AlertDialog {
    @Language("HTML")
    val messageWithLink = """$message
            <br>
            <br><a href='${context.getString(R.string.link_scoped_storage_faq)}'>${context.getString(R.string.scoped_storage_learn_more)}</a>
    """.trimIndent().parseAsHtml()
    setMessage(messageWithLink)
    return makeLinksClickable().apply { show() }
}
