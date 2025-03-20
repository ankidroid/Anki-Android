//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.MediaCheckDialog.Type.DIALOG_CONFIRM_MEDIA_CHECK
import com.ichi2.anki.dialogs.MediaCheckDialog.Type.DIALOG_MEDIA_CHECK_RESULTS
import com.ichi2.anki.showError
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
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

        fun tagMissing(missingMediaNotes: List<Long>?)
    }

    private val dialogType: Type
        get() = Type.fromCode(requireArguments().getInt(MEDIA_CHECK_DIALOG_TYPE_KEY))

    private val noHave: List<String>?
        get() = requireArguments().getStringArrayList(NO_HAVE)

    private val unused: List<String>?
        get() = requireArguments().getStringArrayList(UNUSED)

    private val invalid: List<String>?
        get() = requireArguments().getStringArrayList(INVALID)

    private val missingMediaNotes: List<Long>?
        get() = requireArguments().getSerializableCompat<ArrayList<Long>>(MISSING_MEDIA_NOTES)

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
                val report = generateReport(unused, noHave, invalid)

                val dialogBody = setupDialogBody(layoutInflater, report, noHave, unused)

                dialog
                    .setView(dialogBody)
                    .setCancelable(false)
                    .apply {
                        if (unused.isEmpty() && noHave.isEmpty()) {
                            setNegativeButton(R.string.dialog_ok) { _, _ -> activity?.dismissAllDialogFragments() }
                            return@apply
                        }

                        setPositiveButton(
                            TR.mediaCheckDeleteUnused().toSentenceCase(requireContext(), R.string.check_media_delete_unused),
                        ) { _, _ ->
                            (activity as MediaCheckDialogListener?)?.deleteUnused(unused)
                            activity?.dismissAllDialogFragments()
                        }
                        setNeutralButton(R.string.dialog_cancel) { _, _ -> activity?.dismissAllDialogFragments() }

                        if (noHave.isNotEmpty()) {
                            setNegativeButton(TR.mediaCheckAddTag().toSentenceCase(requireContext(), R.string.tag_missing)) { _, _ ->
                                (activity as MediaCheckDialogListener?)?.tagMissing(missingMediaNotes)
                                activity?.dismissAllDialogFragments()
                            }
                        }
                    }.create()
            }
        }
    }

    private fun generateReport(
        unused: List<String>,
        noHave: List<String>,
        invalid: List<String>,
    ): String {
        val report = StringBuilder()
        if (invalid.isNotEmpty()) {
            report.append(String.format(res().getString(R.string.check_media_invalid), invalid.size))
        }

        if (noHave.isNotEmpty()) {
            if (report.isNotEmpty()) {
                report.append("\n")
            }
            report.append(TR.mediaCheckMissingCount(noHave.size))
        }

        if (unused.isNotEmpty()) {
            if (report.isNotEmpty()) {
                report.append("\n")
            }
            report.append(TR.mediaCheckUnusedCount(unused.size))
        }
        if (report.isEmpty()) {
            report.append(res().getString(R.string.check_media_no_unused_missing))
        }
        return report.toString()
    }

    private fun setupDialogBody(
        inflater: LayoutInflater,
        report: String,
        noHave: List<String>,
        unused: List<String>,
    ): LinearLayout {
        val dialogBody = inflater.inflate(R.layout.media_check_dialog_body, null) as LinearLayout
        val reportTextView = dialogBody.findViewById<TextView>(R.id.reportTextView)
        val fileListTextView = dialogBody.findViewById<TextView>(R.id.fileListTextView)

        reportTextView.text = createReportString(report)

        if (unused.isNotEmpty() || noHave.isNotEmpty()) {
            fileListTextView.text = formatMissingAndUnusedFiles(noHave, unused)
            fileListTextView.isScrollbarFadingEnabled = unused.size + noHave.size <= fileListTextView.maxLines
            fileListTextView.movementMethod = ScrollingMovementMethod.getInstance()
            fileListTextView.setTextIsSelectable(true)
        } else {
            fileListTextView.visibility = View.GONE
        }

        return dialogBody
    }

    private fun createReportString(report: String): String =
        """
        |$report
        """.trimMargin().trimIndent()

    private fun formatMissingAndUnusedFiles(
        noHave: List<String>,
        unused: List<String>,
    ): String {
        val noHaveFormatted = noHave.joinToString("\n") { missingMedia -> TR.mediaCheckMissingFile(missingMedia) }
        val unusedFormatted = unused.joinToString("\n") { unusedMedia -> TR.mediaCheckUnusedFile(unusedMedia) }

        return buildString {
            if (noHaveFormatted.isNotEmpty()) {
                append(TR.mediaCheckMissingHeader())
                append("\n")
                append(noHaveFormatted)
                append("\n\n")
            }
            if (unusedFormatted.isNotEmpty()) {
                append(TR.mediaCheckUnusedHeader())
                append("\n")
                append(unusedFormatted)
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
                DIALOG_MEDIA_CHECK_RESULTS -> TR.mediaCheckCheckMediaAction()
            }

    override val dialogHandlerMessage: MediaCheckCompleteDialog
        get() {
            return MediaCheckCompleteDialog(dialogType, noHave, unused, invalid, missingMediaNotes)
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

        /**
         * Key for a list of notes with missing media
         */
        const val MISSING_MEDIA_NOTES = "missingMediaNotes"

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
                    MISSING_MEDIA_NOTES to ArrayList(checkList.missingMediaNotes).toList(),
                )
        }
    }

    class MediaCheckCompleteDialog(
        private val dialogType: Type,
        private val noHave: List<String>?,
        private val unused: List<String>?,
        private val invalid: List<String>?,
        private val missingMediaNotes: List<Long>?,
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
                            ClassCastException(activity.javaClass.simpleName + " is not " + DeckPicker::class.java.simpleName),
                            true,
                        )
                        return
                    }
                    val checkList =
                        MediaCheckResult(
                            noHave ?: arrayListOf(),
                            unused ?: arrayListOf(),
                            invalid ?: arrayListOf(),
                            missingMediaNotes ?: arrayListOf(),
                        )
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
                        MISSING_MEDIA_NOTES to missingMediaNotes,
                    )
            }

        companion object {
            fun fromMessage(message: Message): MediaCheckCompleteDialog {
                val dialogType = Type.fromCode(message.data.getInt(MEDIA_CHECK_DIALOG_TYPE_KEY))
                val noHave = message.data.getStringArrayList(NO_HAVE)
                val unused = message.data.getStringArrayList(UNUSED)
                val invalid = message.data.getStringArrayList(INVALID)
                val missingMediaNotes = message.data.getLongArray(MISSING_MEDIA_NOTES)?.toList()

                return MediaCheckCompleteDialog(dialogType, noHave, unused, invalid, missingMediaNotes)
            }
        }
    }
}
