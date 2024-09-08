//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs.tags

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.OnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.TagsUtil
import com.ichi2.utils.customView
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import timber.log.Timber
import java.io.File
import java.nio.charset.Charset

class TagsDialog : AnalyticsDialogFragment {
    /**
     * Enum that define all possible types of TagsDialog
     */
    enum class DialogType {
        /**
         * Edit tags of note(s)
         */
        EDIT_TAGS,

        /**
         * Filter notes by tags
         */
        FILTER_BY_TAG,

        /**
         * A custom study session filtered by tags
         */
        CUSTOM_STUDY_TAGS
    }

    private var type: DialogType? = null
    private var tags: TagsList? = null
    private var positiveText: String? = null
    private var dialogTitle: String? = null
    private var tagsArrayAdapter: TagsArrayAdapter? = null
    private var toolbarSearchView: AccessibleSearchView? = null
    private var toolbarSearchItem: MenuItem? = null
    private var noTagsTextView: TextView? = null
    private var tagsListRecyclerView: RecyclerView? = null
    private var dialog: AlertDialog? = null
    private val listener: TagsDialogListener?

    private lateinit var selectedOption: CardStateFilter

    /**
     * Constructs a new [TagsDialog] that will communicate the results using the provided listener.
     */
    constructor(listener: TagsDialogListener?) {
        this.listener = listener
    }

    /**
     * Constructs a new [TagsDialog] that will communicate the results using Fragment Result API.
     *
     * @see [Fragment Result API](https://developer.android.com/guide/fragments/communicate.fragment-result)
     */
    constructor() {
        listener = null
    }

    /**
     * @param type the type of dialog @see [DialogType]
     * @param checkedTags tags of the note
     * @param allTags all possible tags in the collection
     * @return Initialized instance of [TagsDialog]
     */
    fun withArguments(context: Context, type: DialogType, checkedTags: List<String>, allTags: List<String>): TagsDialog {
        return withArguments(context, type, checkedTags, null, allTags)
    }

    /**
     * Construct a tags dialog for a collection of notes
     *
     * @param type the type of dialog @see [DialogType]
     * @param checkedTags sum of all checked tags
     * @param uncheckedTags sum of all unchecked tags
     * @param allTags all possible tags in the collection
     * @return Initialized instance of [TagsDialog]
     */
    fun withArguments(
        context: Context,
        type: DialogType,
        checkedTags: List<String>,
        uncheckedTags: List<String>?,
        allTags: List<String>
    ): TagsDialog {
        val data = TagsFile.TagsData(type, checkedTags, uncheckedTags, allTags)
        val file = TagsFile(context.cacheDir, data)
        arguments = this.arguments ?: bundleOf(
            ARG_TAGS_FILE to file
        )
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resizeWhenSoftInputShown(requireActivity().window)

        val tagsFile = requireNotNull(
            BundleCompat.getParcelable(requireArguments(), ARG_TAGS_FILE, TagsFile::class.java)
        ) {
            "$ARG_TAGS_FILE is required"
        }

        val data = tagsFile.getData()
        type = data.type
        tags = TagsList(
            allTags = data.allTags,
            checkedTags = data.checkedTags,
            uncheckedTags = data.uncheckedTags
        )
        isCancelable = true
    }

    private val tagsDialogListener: TagsDialogListener
        get() = listener
            ?: TagsDialogListener.createFragmentResultSender(parentFragmentManager)

    @NeedsTest(
        "In EDIT_TAGS dialog, long-clicking a tag should open the add tag dialog with the clicked tag" +
            "filled as prefix properly. In other dialog types, long-clicking a tag behaves like a short click."
    )
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams")
        val tagsDialogView = LayoutInflater.from(activity).inflate(R.layout.tags_dialog, null, false)
        tagsListRecyclerView = tagsDialogView.findViewById(R.id.tags_dialog_tags_list)
        val tagsListRecyclerView: RecyclerView? = tagsListRecyclerView
        tagsListRecyclerView?.requestFocus()
        val tagsListLayout: RecyclerView.LayoutManager = LinearLayoutManager(activity)
        tagsListRecyclerView?.layoutManager = tagsListLayout
        tagsArrayAdapter = TagsArrayAdapter(tags!!, resources)
        tagsListRecyclerView?.adapter = tagsArrayAdapter
        noTagsTextView = tagsDialogView.findViewById(R.id.tags_dialog_no_tags_textview)
        val noTagsTextView: TextView? = noTagsTextView
        if (tags!!.isEmpty) {
            noTagsTextView?.visibility = View.VISIBLE
        }
        val optionsGroup = tagsDialogView.findViewById<RadioGroup>(R.id.tags_dialog_options_radiogroup)
        for (i in 0 until optionsGroup.childCount) {
            optionsGroup.getChildAt(i).id = i
        }
        optionsGroup.check(0)
        selectedOption = radioButtonIdToCardState(optionsGroup.checkedRadioButtonId)
        optionsGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int -> selectedOption = radioButtonIdToCardState(checkedId) }
        if (type == DialogType.EDIT_TAGS) {
            dialogTitle = resources.getString(R.string.card_details_tags)
            optionsGroup.visibility = View.GONE
            positiveText = getString(R.string.dialog_ok)
            tagsArrayAdapter!!.tagContextAndLongClickListener =
                OnContextAndLongClickListener { v ->
                    createAddTagDialog(v.tag as String)
                    true
                }
        } else {
            dialogTitle = resources.getString(R.string.studyoptions_limit_select_tags)
            positiveText = getString(R.string.select)
            tagsArrayAdapter!!.tagContextAndLongClickListener = OnContextAndLongClickListener { false }
        }
        adjustToolbar(tagsDialogView)
        dialog = AlertDialog.Builder(requireActivity())
            .positiveButton(text = positiveText!!) {
                tagsDialogListener.onSelectedTags(
                    tags!!.copyOfCheckedTagList(),
                    tags!!.copyOfIndeterminateTagList(),
                    selectedOption
                )
            }
            .negativeButton(R.string.dialog_cancel)
            .customView(view = tagsDialogView)
            .create()
        val dialog: AlertDialog? = dialog
        resizeWhenSoftInputShown(dialog?.window!!)
        return dialog
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    private fun radioButtonIdToCardState(id: Int) =
        when (id) {
            0 -> CardStateFilter.ALL_CARDS
            1 -> CardStateFilter.NEW
            2 -> CardStateFilter.DUE
            else -> {
                Timber.w("unexpected value: %d", id)
                CardStateFilter.ALL_CARDS
            }
        }

    private fun adjustToolbar(tagsDialogView: View) {
        val toolbar: Toolbar = tagsDialogView.findViewById(R.id.tags_dialog_toolbar)
        toolbar.title = dialogTitle
        toolbar.inflateMenu(R.menu.tags_dialog_menu)

        val toolbarAddItem = toolbar.menu.findItem(R.id.tags_dialog_action_add)
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_add_white)
        drawable?.setTint(requireContext().getColor(R.color.white))
        toolbarAddItem.icon = drawable

        toolbarAddItem.setOnMenuItemClickListener {
            val query = toolbarSearchView!!.query.toString()
            if (toolbarSearchItem!!.isActionViewExpanded && query.isNotEmpty()) {
                addTag(query)
                toolbarSearchView!!.setQuery("", true)
            } else {
                createAddTagDialog(null)
            }
            true
        }
        toolbarSearchItem = toolbar.menu.findItem(R.id.tags_dialog_action_filter)
        val toolbarSearchItem: MenuItem? = toolbarSearchItem
        toolbarSearchView = toolbarSearchItem?.actionView as AccessibleSearchView
        val queryET = toolbarSearchView!!.findViewById<EditText>(com.google.android.material.R.id.search_src_text)
        queryET.filters = arrayOf(addTagFilter)
        toolbarSearchView!!.queryHint = getString(R.string.filter_tags)
        toolbarSearchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                toolbarSearchView!!.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                val adapter = tagsListRecyclerView!!.adapter as TagsArrayAdapter?
                adapter!!.filter.filter(newText)
                return true
            }
        })
        val checkAllItem = toolbar.menu.findItem(R.id.tags_dialog_action_select_all)
        checkAllItem.setOnMenuItemClickListener {
            val didChange = tags!!.toggleAllCheckedStatuses()
            if (didChange) {
                tagsArrayAdapter!!.notifyDataSetChanged()
            }
            true
        }
        if (type == DialogType.EDIT_TAGS) {
            toolbarSearchView!!.queryHint = getString(R.string.add_new_filter_tags)
        } else {
            toolbarAddItem.isVisible = false
        }
    }

    /**
     * Create an add tag dialog.
     *
     * @param prefixTag: The tag to be prefilled into the EditText section. A trailing '::' will be appended.
     */
    @NeedsTest("The prefixTag should be prefilled properly")
    private fun createAddTagDialog(prefixTag: String?) {
        val addTagDialog = AlertDialog.Builder(requireActivity()).show {
            title(text = getString(R.string.add_tag))
            positiveButton(R.string.dialog_ok)
            negativeButton(R.string.dialog_cancel)
            setView(R.layout.dialog_generic_text_input)
        }.input(
            hint = getString(R.string.tag_name),
            inputType = InputType.TYPE_CLASS_TEXT
        ) { d: AlertDialog?, input: CharSequence ->
            addTag(input.toString())
            d?.dismiss()
        }
        val inputET = addTagDialog.getInputField()
        inputET.filters = arrayOf(addTagFilter)
        if (!prefixTag.isNullOrEmpty()) {
            // utilize the addTagFilter to append '::' properly by appending a space to prefixTag
            inputET.setText("$prefixTag ")
        }
        inputET.setSelection(inputET.text.length)
        addTagDialog.show()
    }

    @VisibleForTesting
    fun addTag(rawTag: String?) {
        if (!rawTag.isNullOrEmpty()) {
            val tag = TagsUtil.getUniformedTag(rawTag)
            val feedbackText: String
            if (tags!!.add(tag)) {
                if (noTagsTextView!!.visibility == View.VISIBLE) {
                    noTagsTextView!!.visibility = View.GONE
                }
                tags!!.add(tag)
                feedbackText = getString(R.string.tag_editor_add_feedback, tag, positiveText)
            } else {
                feedbackText = getString(R.string.tag_editor_add_feedback_existing, tag)
            }
            tags!!.check(tag)
            tagsArrayAdapter!!.sortData()
            tagsArrayAdapter!!.notifyDataSetChanged()
            // Expand to reveal the newly added tag.
            tagsArrayAdapter!!.filter.apply {
                setExpandTarget(tag)
                refresh()
            }

            // Show a snackbar to let the user know the tag was added successfully
            dialog!!.findViewById<View>(R.id.tags_dialog_snackbar)?.showSnackbar(feedbackText)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun getSearchView(): AccessibleSearchView? {
        return toolbarSearchView
    }

    companion object {
        private const val ARG_TAGS_FILE = "tagsFile"

        /**
         * The filter that constrains the inputted tag.
         * Space is not allowed in a tag. For UX of hierarchical tag, inputting a space will instead
         * insert "::" at the cursor. If there are already some colons in front of the cursor,
         * complete to 2 colons. For example:
         *   "tag"   -- input a space --> "tag::"
         *   "tag:"  -- input a space --> "tag::"
         *   "tag::" -- input a space --> "tag::"
         */
        private val addTagFilter = InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned?, destStart: Int, _: Int ->
            if (!source.subSequence(start, end).contains(' ')) {
                return@InputFilter null
            }
            var previousColonsCnt = 0
            if (dest != null) {
                val previousPart = dest.substring(0, destStart)
                if (previousPart.endsWith("::")) {
                    previousColonsCnt = 2
                } else if (previousPart.endsWith(":")) {
                    previousColonsCnt = 1
                }
            }
            val sb = StringBuilder()
            for (char in source.subSequence(start, end)) {
                if (char == ' ') {
                    if (previousColonsCnt == 0) {
                        sb.append("::")
                    } else if (previousColonsCnt == 1) {
                        sb.append(":")
                    }
                } else {
                    sb.append(char)
                }
                previousColonsCnt = if (char == ':') {
                    previousColonsCnt + 1
                } else {
                    0
                }
            }
            sb
        }
    }
}

/**
 * Temporary file containing the arguments [TagsDialog] uses
 *
 * to avoid [android.os.TransactionTooLargeException]
 *
 */
@WorkerThread
class TagsFile(path: String) : File(path), Parcelable {

    /**
     * @param directory parent directory of the file. Generally it should be the cache directory
     * @param data data for the dialog to display. Typically [Context.getCacheDir]
     */
    constructor(directory: File, data: TagsData) : this(createTempFile("tagsDialog", ".tmp", directory).path) {
        // PERF: Use an alternate format
        // https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md
        val jsonEncoded = Json.encodeToString(data)
        Timber.d("persisting tags to disk, length: %d", jsonEncoded.length)
        FileUtils.writeStringToFile(this, jsonEncoded, Charset.forName("UTF-8"))
    }

    fun getData(): TagsData {
        // PERF!!: This takes ~2 seconds with AnKing
        val jsonEncoded = FileUtils.readFileToString(this, Charset.forName("UTF-8"))
        return Json.decodeFromString<TagsData>(jsonEncoded)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(path)
    }

    companion object {
        @JvmField
        @Suppress("unused")
        val CREATOR = object : Parcelable.Creator<TagsFile> {
            override fun createFromParcel(source: Parcel?): TagsFile {
                return TagsFile(source!!.readString()!!)
            }

            override fun newArray(size: Int): Array<TagsFile> {
                return arrayOf()
            }
        }
    }

    @Serializable
    data class TagsData(
        val type: TagsDialog.DialogType,
        val checkedTags: List<String>,
        val uncheckedTags: List<String>?,
        val allTags: List<String>
    )
}
