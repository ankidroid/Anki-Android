//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.ichi2.anki.R

abstract class MediaCheckDialog : AsyncDialogFragment() {
    interface MediaCheckDialogListener {
        fun showMediaCheckDialog(dialog: MediaCheckDialog)
        fun showMediaCheckDialog(dialog: MediaCheckDialog, checkList: List<List<String>>)
        fun mediaCheck()
        fun deleteUnused(unused: List<String>)
        fun dismissAllDialogFragments()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val dialog = MaterialDialog(requireActivity())
        dialog.title(text = notificationTitle)
        return dialog
    }

    fun dismissAllDialogFragments() {
        (activity as MediaCheckDialogListener).dismissAllDialogFragments()
    }

    override val notificationTitle = getString(R.string.app_name)

    override val dialogHandlerMessage: Message
        get() {
            val msg = Message.obtain()
            msg.what = DialogHandler.MSG_SHOW_MEDIA_CHECK_COMPLETE_DIALOG
            val b = Bundle()
            b.putStringArrayList("nohave", requireArguments().getStringArrayList("nohave"))
            b.putStringArrayList("unused", requireArguments().getStringArrayList("unused"))
            b.putStringArrayList("invalid", requireArguments().getStringArrayList("invalid"))
            b.putInt("dialogType", requireArguments().getInt("dialogType"))
            msg.data = b
            return msg
        }

    companion object {
        const val DIALOG_CONFIRM_MEDIA_CHECK = 0

        fun newInstance(dialog: MediaCheckDialog) =
            dialog.apply {
                arguments = Bundle()
            }

        fun newInstance(dialog: MediaCheckDialog, checkList: List<List<String?>?>) =
            dialog.apply {
                arguments = Bundle().apply {
                    putStringArrayList("nohave", ArrayList(checkList[0]!!.toMutableList()))
                    putStringArrayList("unused", ArrayList(checkList[1]!!.toMutableList()))
                    putStringArrayList("invalid", ArrayList(checkList[2]!!.toMutableList()))
                }
            }
    }
}

class DialogConfirmMediaCheck() : MediaCheckDialog() {
    override val notificationTitle = getString(R.string.check_media_title)

    override val notificationMessage = getString(R.string.check_media_warning)

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        return dialog.show {
            message(text = notificationMessage)
            positiveButton(R.string.dialog_ok) {
                (activity as MediaCheckDialogListener).mediaCheck()
                (activity as MediaCheckDialogListener?)
                    ?.dismissAllDialogFragments()
            }
            negativeButton(R.string.dialog_cancel) {
                (activity as MediaCheckDialogListener?)
                    ?.dismissAllDialogFragments()
            }
            cancelable(true)
        }
    }
}

class DialogMediaCheckResults() : MediaCheckDialog() {

    override val notificationMessage = getString(R.string.check_media_acknowledge)

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val nohave = requireArguments().getStringArrayList("nohave")
        val unused = requireArguments().getStringArrayList("unused")
        val invalid = requireArguments().getStringArrayList("invalid")
        // Generate report
        val report = StringBuilder()
        if (invalid!!.isNotEmpty()) {
            report.append(
                String.format(
                    res().getString(R.string.check_media_invalid),
                    invalid.size
                )
            )
        }
        if (unused!!.isNotEmpty()) {
            if (report.isNotEmpty()) {
                report.append("\n")
            }
            report.append(
                String.format(
                    res().getString(R.string.check_media_unused),
                    unused.size
                )
            )
        }
        if (nohave!!.isNotEmpty()) {
            if (report.isNotEmpty()) {
                report.append("\n")
            }
            report.append(
                String.format(
                    res().getString(R.string.check_media_nohave),
                    nohave.size
                )
            )
        }
        if (report.isEmpty()) {
            report.append(res().getString(R.string.check_media_no_unused_missing))
        }

        // We also prefix the report with a message about the media db being rebuilt, since
        // we do a full media scan and update the db on each media check on AnkiDroid.
        val reportStr = """
                    ${res().getString(R.string.check_media_db_updated)}

                    $report
        """.trimIndent()
        val dialogBody =
            layoutInflater.inflate(R.layout.media_check_dialog_body, null) as LinearLayout
        val reportTextView = dialogBody.findViewById<TextView>(R.id.reportTextView)
        val fileListTextView = dialogBody.findViewById<TextView>(R.id.fileListTextView)
        reportTextView.text = reportStr
        if (unused.isNotEmpty()) {
            reportTextView.append(getString(R.string.unused_strings))
            fileListTextView.append(TextUtils.join("\n", unused))
            fileListTextView.isScrollbarFadingEnabled =
                unused.size <= fileListTextView.maxLines
            fileListTextView.movementMethod = ScrollingMovementMethod.getInstance()
            dialog.positiveButton(R.string.check_media_delete_unused) {
                (activity as MediaCheckDialogListener).deleteUnused(unused)
                dismissAllDialogFragments()
            }
                .negativeButton(R.string.dialog_cancel) {
                    (activity as MediaCheckDialogListener?)
                        ?.dismissAllDialogFragments()
                }
        } else {
            fileListTextView.visibility = View.GONE
            dialog.negativeButton(R.string.dialog_ok) {
                (activity as MediaCheckDialogListener).dismissAllDialogFragments()
            }
        }
        return dialog.show {
            customView(view = dialogBody)
            cancelable(false)
        }
    }
}
