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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.internal.MDButton
import com.ichi2.anki.*
import com.ichi2.anki.analytics.Acra
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.CriticalException
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.model.AnkiDroidGitHub
import com.ichi2.anki.model.AnkiDroidGitHub.GitHubIssue.GitHubIssueLink.LinkType
import com.ichi2.anki.model.UrlOpener
import com.ichi2.libanki.CollectionGetter
import com.ichi2.utils.DialogUtils
import com.ichi2.utils.ExceptionUtil
import com.ichi2.utils.ExtendedFragmentFactory
import timber.log.Timber

/**
 * Note: We are not an async fragment.
 * We need this to be explicitly closed, showing in a notification is not enough
 *
 * Contact Developers => Keep Dialog Open and open GitHub
 * Ignore => Warn user and allow them to ignore the dialog
 * Close => Ignore for this session
 */
class CriticalExceptionDialog(
    private val mListener: CriticalExceptionDialogClosedListener,
    private val mCollectionGetter: CollectionGetter,
    private val mUrlOpener: UrlOpener
) : AnalyticsDialogFragment() {
    interface CriticalExceptionDialogClosedListener {
        fun onCriticalExceptionDialogClosed()
    }

    private lateinit var mDialogView: View
    private lateinit var mDialog: MaterialDialog
    private lateinit var mCriticalException: CriticalException

    @VisibleForTesting
    var mLastIgnoreDialog: MaterialDialog? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)

        this.mDialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_critical_exception, null, false)

        // HACK: we should serialize this as an arg, but it's difficult to serialize a throwable
        this.mCriticalException = (CriticalException.get() ?: return DialogUtils.doNotDisplay(this))

        getDebugInfoButton().setOnClickListener { copyDebugInfoToClipboard() }
        getErrorCodeLabel().text = mCriticalException.getIssue().toString()

        // Checkboxes: disable if they're already set
        val analytics = getEnableAnalyticsCheckbox()
        val reportError = getEnableErrorReportingCheckbox()
        analytics.visibility = if (analyticsEnabled()) View.GONE else View.VISIBLE
        reportError.visibility = if (errorReportingEnabled()) View.GONE else View.VISIBLE

        // After enabling reporting, we show a dialog on exit, inform the user that this is expected
        reportError.setOnCheckedChangeListener { _, b ->
            if (!b) return@setOnCheckedChangeListener
            UIUtils.showThemedToast(requireContext(), R.string.crash_reports_enalbed, false)
        }

        val dialog = MaterialDialog.Builder(requireContext())
            .title(getString(R.string.critical_error_occurred))
            .iconAttr(R.attr.dialogErrorIcon)
            .cancelable(false)
            .customView(mDialogView, true)
            .positiveText(getString(R.string.contact))
            .negativeText(getString(R.string.ignore))
            .neutralText(R.string.close)
            .onNeutral { _, _ -> onBeforeDialogClose() }
            .build()

        // We don't want the dialog closed. Set the listener and not the callback
        dialog.getActionButton(DialogAction.NEGATIVE).setOnClickListener { showFutureFailureWarning() }
        dialog.getActionButton(DialogAction.POSITIVE).setOnClickListener { openGitHubIssue() }

        mDialog = dialog

        return dialog
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun getActionButton(which: DialogAction): MDButton = mDialog.getActionButton(which)

    private fun onBeforeDialogClose() {
        // Both the analytics and the crash reporting checkbox can be checked.

        // If either are enabled, we want the previously ignored event to be sent

        if (isChecked(getEnableAnalyticsCheckbox())) {
            enableAnalytics(mCriticalException)
        }

        if (isChecked(getEnableErrorReportingCheckbox())) {
            enableErrorReporting(mCriticalException)
        }

        mListener.onCriticalExceptionDialogClosed()
    }

    private fun enableErrorReporting(ex: CriticalException) {
        Timber.i("Enabling error reporting (ASK): Checkbox was checked")

        Acra.enableWithAskDialog(requireContext())

        AnkiDroidApp.sendExceptionReport(ex.getException(), "CriticalExceptionDialog::enableErrorReporting")
    }

    private fun enableAnalytics(ex: CriticalException) {
        Timber.i("Enabling analytics: Checkbox was checked")

        // TODO: Ensure that this is not async
        UsageAnalytics.setEnabled(true)

        UsageAnalytics.sendAnalyticsCriticalException(ex)
    }

    /** In here, we hide Checkboxes which aren't in use, ensure that a default won't enable analytics */
    private fun isChecked(cb: CheckBox) = cb.visibility != View.GONE && cb.isChecked

    private fun errorReportingEnabled() = AnkiDroidApp.isAcraEnabled(requireContext(), false)
    private fun analyticsEnabled() = UsageAnalytics.isEnabled()

    fun getDebugInfoButton(): View = mDialogView.findViewById(R.id.copy_debug_info)
    fun getErrorCodeLabel(): TextView = mDialogView.findViewById(R.id.label_error_code)
    fun getEnableErrorReportingCheckbox(): CheckBox = mDialogView.findViewById(R.id.enable_reporting)
    fun getEnableAnalyticsCheckbox(): CheckBox = mDialogView.findViewById(R.id.enable_analytics)

    /** Either: Opens the GitHub issue ID, or shows a page to open a new issue with
     * prefilled information
     *
     * Also shows a toast with information to help the user
     */
    private fun openGitHubIssue() {
        val issueLink = mCriticalException.getGitHubLink()

        val url = issueLink.asUrl()
        if (url == null) {
            Timber.w("url was null: %s", url)
            return
        }

        val toastString = when (issueLink.type) {
            LinkType.KNOWN_ISSUE -> R.string.open_known_github_issue
            LinkType.NEW_ISSUE -> R.string.open_unknown_github_issue
        }
        UIUtils.showThemedToast(requireContext(), toastString, false)
        mUrlOpener.openUrl(url)
    }

    fun CriticalException.getGitHubLink(): AnkiDroidGitHub.GitHubIssue.GitHubIssueLink {
        return when (val issueId = this.getIssue().gitHubIssueId) {
            null -> AnkiDroidGitHub.getIssueLink(
                // Note: Do not translate this, as it's sent to GitHub for developers to use
                AnkiDroidGitHub.GitHubIssue.gitHubBug(
                    // use " - " for nested messages
                    title = "[Critical Exception] ${ExceptionUtil.getExceptionMessage(this.getException(), " - ")}",
                    body = "```\n${ExceptionUtil.getFullStackTrace(this.getException())}\n```"
                )
            )
            else -> AnkiDroidGitHub.getIssueLink(issueId)
        }
    }

    /** Displays a dialog stating that future AnkiDroid updates may no longer work
     * Ignore => Close both dialogs and save preference
     * Cancel => Return to CriticalExceptionDialog
     */
    private fun showFutureFailureWarning() {
        val ignoreExceptionWarningDialog = MaterialDialog.Builder(requireActivity())
            .content("Ignoring this error without resolving it may mean AnkiDroid will fail to run in the future")
            .negativeText(R.string.dialog_cancel)
            .positiveText(R.string.ignore)
            .onPositive { _, _ ->
                run {
                    ignoreException()
                    onBeforeDialogClose()
                    this.dismiss()
                }
            }

        val dialog = ignoreExceptionWarningDialog.build()
        this.mLastIgnoreDialog = dialog
        dialog.show()
    }

    /** Ensures that an exception will never be warned about again */
    private fun ignoreException() = CriticalException.ignore(mCriticalException)

    /** Copies the Exception stacktrace and the app debug info to the clipboard */
    private fun copyDebugInfoToClipboard() {
        // copy both the Exception data and Anki Debug Info to the clipboard
        val debugInfo = getDebugInfo()
        val clipboardManager = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(CriticalExceptionDialog::class.java.simpleName, debugInfo))
            UIUtils.showThemedToast(requireContext(), getString(R.string.about_ankidroid_successfully_copied_debug), true)
        } else {
            UIUtils.showThemedToast(requireContext(), getString(R.string.about_ankidroid_error_copy_debug_info), true)
        }
    }

    /** Obtains the exception stacktrace and the app debug info */
    @VisibleForTesting
    fun getDebugInfo(): String {
        val debugInfo = Info.getDebugInfo(requireContext()) { mCollectionGetter.col }
        val stackTrace = ExceptionUtil.getFullStackTrace(mCriticalException.getException())
        // Show the debug info before the stack trace
        // It's shorter, fixed length and the ACRA ID likely contains everything
        return debugInfo +
            "\n\n----\n\n" +
            stackTrace
    }

    private fun requireAnkiActivity() = (requireActivity() as AnkiActivity)

    class Factory(
        private val mListener: CriticalExceptionDialogClosedListener,
        private val mCollectionGetter: CollectionGetter,
        private val mUrlOpener: UrlOpener
    ) : ExtendedFragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            val cls = loadFragmentClass(classLoader, className)
            return if (cls == CriticalExceptionDialog::class.java) {
                newCriticalExceptionDialog()
            } else super.instantiate(classLoader, className)
        }

        fun hasDataForDialog(): Boolean {
            return CriticalException.get() != null
        }

        fun newCriticalExceptionDialog(): CriticalExceptionDialog {
            return CriticalExceptionDialog(mListener, mCollectionGetter, mUrlOpener)
        }
    }
}
