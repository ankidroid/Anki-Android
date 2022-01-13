//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import java.util.*

class MediaCheckDialog : AsyncDialogFragment() {
    interface MediaCheckDialogListener {
        fun showMediaCheckDialog(dialogType: Int)
        fun showMediaCheckDialog(dialogType: Int, checkList: List<List<String>>)
        fun mediaCheck()
        fun deleteUnused(unused: List<String?>?)
        fun dismissAllDialogFragments()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val builder = MaterialDialog.Builder(requireActivity())
        builder.title(getNotificationTitle())
        return when (requireArguments().getInt("dialogType")) {
            DIALOG_CONFIRM_MEDIA_CHECK -> {
                builder.content(getNotificationMessage())
                    .positiveText(res().getString(R.string.dialog_ok))
                    .negativeText(res().getString(R.string.dialog_cancel))
                    .cancelable(true)
                    .onPositive { _: MaterialDialog?, _: DialogAction? ->
                        (activity as MediaCheckDialogListener?)!!.mediaCheck()
                        (activity as MediaCheckDialogListener?)
                            ?.dismissAllDialogFragments()
                    }
                    .onNegative { _: MaterialDialog?, _: DialogAction? ->
                        (activity as MediaCheckDialogListener?)
                            ?.dismissAllDialogFragments()
                    }
                    .show()
            }
            DIALOG_MEDIA_CHECK_RESULTS -> {
                val nohave = requireArguments().getStringArrayList("nohave")
                val unused = requireArguments().getStringArrayList("unused")
                val invalid = requireArguments().getStringArrayList("invalid")
                // Generate report
                val report = StringBuilder()
                if (invalid!!.isNotEmpty()) {
                    report.append(String.format(res().getString(R.string.check_media_invalid), invalid.size))
                }
                if (unused!!.isNotEmpty()) {
                    if (report.isNotEmpty()) {
                        report.append("\n")
                    }
                    report.append(String.format(res().getString(R.string.check_media_unused), unused.size))
                }
                if (nohave!!.isNotEmpty()) {
                    if (report.isNotEmpty()) {
                        report.append("\n")
                    }
                    report.append(String.format(res().getString(R.string.check_media_nohave), nohave.size))
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
                val dialogBody = layoutInflater.inflate(R.layout.media_check_dialog_body, null) as LinearLayout
                val reportTextView = dialogBody.findViewById<TextView>(R.id.reportTextView)
                val fileListTextView = dialogBody.findViewById<TextView>(R.id.fileListTextView)
                reportTextView.text = reportStr
                if (unused.isNotEmpty()) {
                    reportTextView.append(getString(R.string.unused_strings))
                    fileListTextView.append(TextUtils.join("\n", unused))
                    fileListTextView.isScrollbarFadingEnabled = unused.size <= fileListTextView.maxLines
                    fileListTextView.movementMethod = ScrollingMovementMethod.getInstance()
                    builder.negativeText(res().getString(R.string.dialog_cancel))
                        .positiveText(res().getString(R.string.check_media_delete_unused))
                        .onNegative { _: MaterialDialog?, _: DialogAction? ->
                            (activity as MediaCheckDialogListener?)
                                ?.dismissAllDialogFragments()
                        }
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            (activity as MediaCheckDialogListener?)!!.deleteUnused(unused)
                            dismissAllDialogFragments()
                        }
                } else {
                    fileListTextView.visibility = View.GONE
                    builder.negativeText(res().getString(R.string.dialog_ok))
                        .onNegative { _: MaterialDialog?, _: DialogAction? -> (activity as MediaCheckDialogListener?)!!.dismissAllDialogFragments() }
                }
                builder
                    .customView(dialogBody, false)
                    .cancelable(false)
                    .show()
            }
            else -> null!!
        }
    }

    fun dismissAllDialogFragments() {
        (activity as MediaCheckDialogListener?)!!.dismissAllDialogFragments()
    }

    override fun getNotificationMessage(): String {
        return when (requireArguments().getInt("dialogType")) {
            DIALOG_CONFIRM_MEDIA_CHECK -> res().getString(R.string.check_media_warning)
            DIALOG_MEDIA_CHECK_RESULTS -> res().getString(R.string.check_media_acknowledge)
            else -> res().getString(R.string.app_name)
        }
    }

    override fun getNotificationTitle(): String {
        return if (requireArguments().getInt("dialogType") == DIALOG_CONFIRM_MEDIA_CHECK) {
            res().getString(R.string.check_media_title)
        } else res().getString(R.string.app_name)
    }

    override fun getDialogHandlerMessage(): Message {
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
        const val DIALOG_MEDIA_CHECK_RESULTS = 1
        @JvmStatic
        fun newInstance(dialogType: Int): MediaCheckDialog {
            val f = MediaCheckDialog()
            val args = Bundle()
            args.putInt("dialogType", dialogType)
            f.arguments = args
            return f
        }

        @JvmStatic
        fun newInstance(dialogType: Int, checkList: List<List<String?>?>): MediaCheckDialog {
            val f = MediaCheckDialog()
            val args = Bundle()
            args.putStringArrayList("nohave", ArrayList(checkList[0]!!.toMutableList()))
            args.putStringArrayList("unused", ArrayList(checkList[1]!!.toMutableList()))
            args.putStringArrayList("invalid", ArrayList(checkList[2]!!.toMutableList()))
            args.putInt("dialogType", dialogType)
            f.arguments = args
            return f
        }
    }
}
