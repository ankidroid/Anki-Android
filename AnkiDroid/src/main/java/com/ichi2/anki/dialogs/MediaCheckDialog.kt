//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.MediaCheckDialog.Type.DIALOG_CONFIRM_MEDIA_CHECK
import com.ichi2.anki.dialogs.MediaCheckDialog.Type.DIALOG_MEDIA_CHECK_RESULTS
import com.ichi2.anki.showError
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.libanki.MediaCheckResult

class MediaCheckDialog : AsyncDialogFragment() {
    interface MediaCheckDialogListener {
        fun showMediaCheckDialog(dialogType: Type)

        fun showMediaCheckDialog(
            dialogType: Type,
            checkList: MediaCheckResult,
        )

        fun mediaCheck()

        fun deleteUnused(unused: List<String>)
    }

    private val dialogType: Type
        get() = Type.fromCode(requireArguments().getInt(MEDIA_CHECK_DIALOG_TYPE_KEY))

    private val noHave: List<String>?
        get() = requireArguments().getStringArrayList(NO_HAVE)

    private val unused: List<String>?
        get() = requireArguments().getStringArrayList(UNUSED)

    private val invalid: List<String>?
        get() = requireArguments().getStringArrayList(INVALID)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val dialog =
            AlertDialog
                .Builder(requireContext())
                .setTitle(notificationTitle)
        return when (dialogType) {
            DIALOG_CONFIRM_MEDIA_CHECK -> {
                dialog
                    .setMessage(notificationMessage)
                    .setPositiveButton(R.string.dialog_ok) { _, _ ->
                        (activity as MediaCheckDialogListener?)?.mediaCheck()
                        activity?.dismissAllDialogFragments()
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ ->
                        activity?.dismissAllDialogFragments()
                    }.create()
            }
            DIALOG_MEDIA_CHECK_RESULTS -> {
                val noHave = noHave!!
                val unused = unused!!
                val invalid = invalid!!
                // Generate report
                val report = StringBuilder()
                if (invalid.isNotEmpty()) {
                    report.append(String.format(res().getString(R.string.check_media_invalid), invalid.size))
                }
                if (unused.isNotEmpty()) {
                    if (report.isNotEmpty()) {
                        report.append("\n")
                    }
                    report.append(String.format(res().getString(R.string.check_media_unused), unused.size))
                }
                if (noHave.isNotEmpty()) {
                    if (report.isNotEmpty()) {
                        report.append("\n")
                    }
                    report.append(String.format(res().getString(R.string.check_media_nohave), noHave.size))
                }
                if (report.isEmpty()) {
                    report.append(res().getString(R.string.check_media_no_unused_missing))
                }

                // We also prefix the report with a message about the media db being rebuilt, since
                // we do a full media scan and update the db on each media check on AnkiDroid.
                val reportStr =
                    """
                    |${res().getString(R.string.check_media_db_updated)}
                    
                    |$report
                    """.trimMargin().trimIndent()
                val dialogBody = layoutInflater.inflate(R.layout.media_check_dialog_body, null) as LinearLayout
                val reportTextView = dialogBody.findViewById<TextView>(R.id.reportTextView)
                val fileListTextView = dialogBody.findViewById<TextView>(R.id.fileListTextView)
                reportTextView.text = reportStr
                if (unused.isNotEmpty()) {
                    reportTextView.append(getString(R.string.unused_strings))
                    fileListTextView.append(unused.joinToString("\n"))
                    fileListTextView.isScrollbarFadingEnabled = unused.size <= fileListTextView.maxLines
                    fileListTextView.movementMethod = ScrollingMovementMethod.getInstance()
                    fileListTextView.setTextIsSelectable(true)
                    dialog
                        .setPositiveButton(R.string.check_media_delete_unused) { _, _ ->
                            (activity as MediaCheckDialogListener?)?.deleteUnused(unused)
                            activity?.dismissAllDialogFragments()
                        }.setNegativeButton(R.string.dialog_cancel) { _, _ ->
                            activity?.dismissAllDialogFragments()
                        }
                } else {
                    fileListTextView.visibility = View.GONE
                    dialog.setNegativeButton(R.string.dialog_ok) { _, _ ->
                        activity?.dismissAllDialogFragments()
                    }
                }
                dialog
                    .setView(dialogBody)
                    .setCancelable(false)
                    .create()
            }
        }
    }

    override val notificationMessage: String
        get() =
            when (dialogType) {
                DIALOG_CONFIRM_MEDIA_CHECK -> res().getString(R.string.check_media_warning)
                DIALOG_MEDIA_CHECK_RESULTS -> res().getString(R.string.check_media_acknowledge)
            }

    override val notificationTitle: String
        get() =
            when (dialogType) {
                DIALOG_CONFIRM_MEDIA_CHECK -> res().getString(R.string.check_media_title)
                DIALOG_MEDIA_CHECK_RESULTS -> res().getString(R.string.app_name)
            }

    override val dialogHandlerMessage: MediaCheckCompleteDialog
        get() {
            return MediaCheckCompleteDialog(dialogType, noHave, unused, invalid)
        }

    enum class Type(
        val code: Int,
    ) {
        DIALOG_CONFIRM_MEDIA_CHECK(0),
        DIALOG_MEDIA_CHECK_RESULTS(1),
        ;

        companion object {
            fun fromCode(code: Int) = Type.entries.first { code == it.code }
        }
    }

    companion object {
        /**
         * Key for an ordinal in the Type.entries.
         */
        const val MEDIA_CHECK_DIALOG_TYPE_KEY = "dialogType"

        /**
         * Key for an array of strings of name of missing media
         */
        const val NO_HAVE = "noHave"

        /**
         * Key for an array of strings of name of unused media
         */
        const val UNUSED = "unused"

        /**
         * Key for an array of strings of name of invalid media
         */
        const val INVALID = "invalid"

        @CheckResult
        fun newInstance(dialogType: Type) =
            MediaCheckDialog().apply { arguments = bundleOf(MEDIA_CHECK_DIALOG_TYPE_KEY to dialogType.code) }

        // TODO Instead of putting string arrays into the bundle,
        //   make MediaCheckResult parcelable with @Parcelize and put it instead
        fun newInstance(
            dialogType: Type,
            checkList: MediaCheckResult,
        ) = MediaCheckDialog().apply {
            arguments =
                bundleOf(
                    NO_HAVE to ArrayList(checkList.missingFileNames),
                    UNUSED to ArrayList(checkList.unusedFileNames),
                    INVALID to ArrayList(checkList.invalidFileNames),
                    MEDIA_CHECK_DIALOG_TYPE_KEY to dialogType.code,
                )
        }
    }

    class MediaCheckCompleteDialog(
        private val dialogType: Type,
        private val noHave: List<String>?,
        private val unused: List<String>?,
        private val invalid: List<String>?,
    ) : DialogHandlerMessage(WhichDialogHandler.MSG_SHOW_MEDIA_CHECK_COMPLETE_DIALOG, "MediaCheckCompleteDialog") {
        override fun handleAsyncMessage(activity: AnkiActivity) {
            // Media check results
            when (dialogType) {
                DIALOG_MEDIA_CHECK_RESULTS -> {
                    // we may be called via any AnkiActivity but media check is a DeckPicker thing
                    if (activity !is DeckPicker) {
                        showError(
                            activity,
                            activity.getString(R.string.something_wrong),
                            ClassCastException(activity.javaClass.simpleName + " is not " + DeckPicker.javaClass.simpleName),
                            true,
                        )
                        return
                    }
                    val checkList = MediaCheckResult(noHave ?: arrayListOf(), unused ?: arrayListOf(), invalid ?: arrayListOf())
                    activity.showMediaCheckDialog(dialogType, checkList)
                }
                DIALOG_CONFIRM_MEDIA_CHECK -> { }
            }
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                what = this@MediaCheckCompleteDialog.what
                data =
                    bundleOf(
                        NO_HAVE to noHave,
                        UNUSED to unused,
                        INVALID to invalid,
                        MEDIA_CHECK_DIALOG_TYPE_KEY to dialogType,
                    )
            }

        companion object {
            fun fromMessage(message: Message): MediaCheckCompleteDialog {
                val dialogType = Type.fromCode(message.data.getInt(MEDIA_CHECK_DIALOG_TYPE_KEY))
                val noHave = message.data.getStringArrayList(NO_HAVE)
                val unused = message.data.getStringArrayList(UNUSED)
                val invalid = message.data.getStringArrayList(INVALID)
                return MediaCheckCompleteDialog(dialogType, noHave, unused, invalid)
            }
        }
    }
}
