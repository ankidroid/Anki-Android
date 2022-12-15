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
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.ichi2.anki.R
import com.ichi2.libanki.MediaCheckResult

class MediaCheckDialog : AsyncDialogFragment() {
    interface MediaCheckDialogListener {
        fun showMediaCheckDialog(dialogType: Int)
        fun showMediaCheckDialog(dialogType: Int, checkList: MediaCheckResult)
        fun mediaCheck()
        fun deleteUnused(unused: List<String>)
        fun dismissAllDialogFragments()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val dialog = MaterialDialog(requireActivity())
        dialog.title(text = notificationTitle)
        return when (requireArguments().getInt("dialogType")) {
            DIALOG_CONFIRM_MEDIA_CHECK -> {
                dialog.show {
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
            DIALOG_MEDIA_CHECK_RESULTS -> {
                val nohave = requireArguments().getStringArrayList("nohave")
                val unused = requireArguments().getStringArrayList("unused")
                val invalid = requireArguments().getStringArrayList("invalid")
                // Generate report
                val report = StringBuilder()
                if (invalid!!.isNotEmpty()) {
                    report.append(String.format(resources.getString(R.string.check_media_invalid), invalid.size))
                }
                if (unused!!.isNotEmpty()) {
                    if (report.isNotEmpty()) {
                        report.append("\n")
                    }
                    report.append(String.format(resources.getString(R.string.check_media_unused), unused.size))
                }
                if (nohave!!.isNotEmpty()) {
                    if (report.isNotEmpty()) {
                        report.append("\n")
                    }
                    report.append(String.format(resources.getString(R.string.check_media_nohave), nohave.size))
                }
                if (report.isEmpty()) {
                    report.append(resources.getString(R.string.check_media_no_unused_missing))
                }

                // We also prefix the report with a message about the media db being rebuilt, since
                // we do a full media scan and update the db on each media check on AnkiDroid.
                val reportStr = """
                    |${resources.getString(R.string.check_media_db_updated)}
                    
                    |$report
                """.trimMargin().trimIndent()
                val dialogBody = layoutInflater.inflate(R.layout.media_check_dialog_body, null) as LinearLayout
                val reportTextView = dialogBody.findViewById<TextView>(R.id.reportTextView)
                val fileListTextView = dialogBody.findViewById<TextView>(R.id.fileListTextView)
                reportTextView.text = reportStr
                if (unused.isNotEmpty()) {
                    reportTextView.append(getString(R.string.unused_strings))
                    fileListTextView.append(TextUtils.join("\n", unused))
                    fileListTextView.isScrollbarFadingEnabled = unused.size <= fileListTextView.maxLines
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
                dialog.show {
                    customView(view = dialogBody)
                    cancelable(false)
                }
            }
            else -> null!!
        }
    }

    fun dismissAllDialogFragments() {
        (activity as MediaCheckDialogListener).dismissAllDialogFragments()
    }

    override val notificationMessage: String
        get() {
            return when (requireArguments().getInt("dialogType")) {
                DIALOG_CONFIRM_MEDIA_CHECK -> resources.getString(R.string.check_media_warning)
                DIALOG_MEDIA_CHECK_RESULTS -> resources.getString(R.string.check_media_acknowledge)
                else -> resources.getString(R.string.app_name)
            }
        }

    override val notificationTitle: String
        get() {
            return if (requireArguments().getInt("dialogType") == DIALOG_CONFIRM_MEDIA_CHECK) {
                resources.getString(R.string.check_media_title)
            } else resources.getString(R.string.app_name)
        }

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
        const val DIALOG_MEDIA_CHECK_RESULTS = 1
        fun newInstance(dialogType: Int): MediaCheckDialog {
            val f = MediaCheckDialog()
            val args = Bundle()
            args.putInt("dialogType", dialogType)
            f.arguments = args
            return f
        }

        // TODO Instead of putting string arrays into the bundle,
        //   make MediaCheckResult parcelable with @Parcelize and put it instead.
        // TODO Extract keys to constants
        fun newInstance(dialogType: Int, checkList: MediaCheckResult): MediaCheckDialog {
            val f = MediaCheckDialog()
            val args = Bundle()
            args.putStringArrayList("nohave", ArrayList(checkList.missingFileNames))
            args.putStringArrayList("unused", ArrayList(checkList.unusedFileNames))
            args.putStringArrayList("invalid", ArrayList(checkList.invalidFileNames))
            args.putInt("dialogType", dialogType)
            f.arguments = args
            return f
        }
    }
}
