//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs.tags

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.TagsUtil
import timber.log.Timber

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
        CUSTOM_STUDY_TAGS,
    }

    private var mType: DialogType? = null
    private var mTags: TagsList? = null
    private var mPositiveText: String? = null
    private var mDialogTitle: String? = null
    private var mTagsArrayAdapter: TagsArrayAdapter? = null
    private var mToolbarSearchView: SearchView? = null
    private var mToolbarSearchItem: MenuItem? = null
    private var mNoTagsTextView: TextView? = null
    private var mTagsListRecyclerView: RecyclerView? = null
    private var mDialog: MaterialDialog? = null
    private val mListener: TagsDialogListener?

    private lateinit var selectedOption: CardStateFilter

    /**
     * Constructs a new [TagsDialog] that will communicate the results using the provided listener.
     */
    constructor(listener: TagsDialogListener?) {
        mListener = listener
    }

    /**
     * Constructs a new [TagsDialog] that will communicate the results using Fragment Result API.
     *
     * @see [Fragment Result API](https://developer.android.com/guide/fragments/communicate.fragment-result)
     */
    constructor() {
        mListener = null
    }

    /**
     * @param type the type of dialog @see [DialogType]
     * @param checkedTags tags of the note
     * @param allTags all possible tags in the collection
     * @return Initialized instance of [TagsDialog]
     */
    fun withArguments(
        type: DialogType,
        checkedTags: List<String?>,
        allTags: List<String?>,
    ): TagsDialog {
        return withArguments(type, checkedTags, null, allTags)
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
        type: DialogType,
        checkedTags: List<String?>,
        uncheckedTags: List<String>?,
        allTags: List<String?>,
    ): TagsDialog {
        val args = this.arguments ?: Bundle()
        args.putInt(DIALOG_TYPE_KEY, type.ordinal)
        args.putStringArrayList(CHECKED_TAGS_KEY, ArrayList(checkedTags))
        if (uncheckedTags != null) {
            args.putStringArrayList(UNCHECKED_TAGS_KEY, ArrayList(uncheckedTags))
        }
        args.putStringArrayList(ALL_TAGS_KEY, ArrayList(allTags))
        arguments = args
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resizeWhenSoftInputShown(requireActivity().window)
        mType = DialogType.entries[requireArguments().getInt(DIALOG_TYPE_KEY)]
        mTags =
            TagsList(
                requireArguments().getStringArrayList(ALL_TAGS_KEY)!!,
                requireArguments().getStringArrayList(CHECKED_TAGS_KEY)!!,
                requireArguments().getStringArrayList(UNCHECKED_TAGS_KEY),
            )
        isCancelable = true
    }

    private val tagsDialogListener: TagsDialogListener
        get() =
            mListener
                ?: TagsDialogListener.createFragmentResultSender(parentFragmentManager)

    @NeedsTest(
        "In EDIT_TAGS dialog, long-clicking a tag should open the add tag dialog with the clicked tag" +
            "filled as prefix properly. In other dialog types, long-clicking a tag behaves like a short click.",
    )
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams")
        val tagsDialogView = LayoutInflater.from(activity).inflate(R.layout.tags_dialog, null, false)
        mTagsListRecyclerView = tagsDialogView.findViewById(R.id.tags_dialog_tags_list)
        val tagsListRecyclerView: RecyclerView? = mTagsListRecyclerView
        tagsListRecyclerView?.requestFocus()
        val tagsListLayout: RecyclerView.LayoutManager = LinearLayoutManager(activity)
        tagsListRecyclerView?.layoutManager = tagsListLayout
        mTagsArrayAdapter = TagsArrayAdapter(mTags!!, resources)
        tagsListRecyclerView?.adapter = mTagsArrayAdapter
        mNoTagsTextView = tagsDialogView.findViewById(R.id.tags_dialog_no_tags_textview)
        val noTagsTextView: TextView? = mNoTagsTextView
        if (mTags!!.isEmpty) {
            noTagsTextView?.visibility = View.VISIBLE
        }
        val optionsGroup = tagsDialogView.findViewById<RadioGroup>(R.id.tags_dialog_options_radiogroup)
        for (i in 0 until optionsGroup.childCount) {
            optionsGroup.getChildAt(i).id = i
        }
        optionsGroup.check(0)
        selectedOption = radioButtonIdToCardState(optionsGroup.checkedRadioButtonId)
        optionsGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int -> selectedOption = radioButtonIdToCardState(checkedId) }
        if (mType == DialogType.EDIT_TAGS) {
            mDialogTitle = resources.getString(R.string.card_details_tags)
            optionsGroup.visibility = View.GONE
            mPositiveText = getString(R.string.dialog_ok)
            mTagsArrayAdapter!!.tagLongClickListener =
                View.OnLongClickListener { v ->
                    createAddTagDialog(v.tag as String)
                    true
                }
        } else {
            mDialogTitle = resources.getString(R.string.studyoptions_limit_select_tags)
            mPositiveText = getString(R.string.select)
            mTagsArrayAdapter!!.tagLongClickListener = View.OnLongClickListener { false }
        }
        adjustToolbar(tagsDialogView)
        mDialog =
            MaterialDialog(requireActivity())
                .positiveButton(text = mPositiveText!!) {
                    tagsDialogListener.onSelectedTags(
                        mTags!!.copyOfCheckedTagList(),
                        mTags!!.copyOfIndeterminateTagList(),
                        selectedOption,
                    )
                }
                .negativeButton(R.string.dialog_cancel)
                .customView(view = tagsDialogView, noVerticalPadding = true)
        val dialog: MaterialDialog? = mDialog
        resizeWhenSoftInputShown(dialog?.window!!)
        return dialog
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
        toolbar.title = mDialogTitle
        toolbar.inflateMenu(R.menu.tags_dialog_menu)

        val toolbarAddItem = toolbar.menu.findItem(R.id.tags_dialog_action_add)
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_add_white)
        drawable?.setTint(requireContext().getColor(R.color.white))
        toolbarAddItem.icon = drawable

        toolbarAddItem.setOnMenuItemClickListener {
            val query = mToolbarSearchView!!.query.toString()
            if (mToolbarSearchItem!!.isActionViewExpanded && query.isNotEmpty()) {
                addTag(query)
                mToolbarSearchView!!.setQuery("", true)
            } else {
                createAddTagDialog(null)
            }
            true
        }
        mToolbarSearchItem = toolbar.menu.findItem(R.id.tags_dialog_action_filter)
        val toolbarSearchItem: MenuItem? = mToolbarSearchItem
        mToolbarSearchView = toolbarSearchItem?.actionView as SearchView
        val queryET = mToolbarSearchView!!.findViewById<EditText>(com.google.android.material.R.id.search_src_text)
        queryET.filters = arrayOf(addTagFilter)
        mToolbarSearchView!!.queryHint = getString(R.string.filter_tags)
        mToolbarSearchView!!.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    mToolbarSearchView!!.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    val adapter = mTagsListRecyclerView!!.adapter as TagsArrayAdapter?
                    adapter!!.filter.filter(newText)
                    return true
                }
            },
        )
        val checkAllItem = toolbar.menu.findItem(R.id.tags_dialog_action_select_all)
        checkAllItem.setOnMenuItemClickListener {
            val didChange = mTags!!.toggleAllCheckedStatuses()
            if (didChange) {
                mTagsArrayAdapter!!.notifyDataSetChanged()
            }
            true
        }
        if (mType == DialogType.EDIT_TAGS) {
            mToolbarSearchView!!.queryHint = getString(R.string.add_new_filter_tags)
        } else {
            toolbarAddItem.isVisible = false
        }
    }

    /**
     * A wrapper function around dialog.getInputEditText() to get non null [EditText]
     */
    // TODO: Remove this, no longer needed
    private fun requireDialogInputEditText(dialog: MaterialDialog): EditText {
        return dialog.getInputField()
    }

    /**
     * Create an add tag dialog.
     *
     * @param prefixTag: The tag to be prefilled into the EditText section. A trailing '::' will be appended.
     */
    @NeedsTest("The prefixTag should be prefilled properly")
    private fun createAddTagDialog(prefixTag: String?) {
        val addTagDialog =
            MaterialDialog(requireActivity())
                .title(text = getString(R.string.add_tag))
                .positiveButton(R.string.dialog_ok)
                .negativeButton(R.string.dialog_cancel)
                .input(
                    hintRes = R.string.tag_name,
                    inputType = InputType.TYPE_CLASS_TEXT,
                ) { _: MaterialDialog?, input: CharSequence -> addTag(input.toString()) }
        val inputET = requireDialogInputEditText(addTagDialog)
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
            if (mTags!!.add(tag)) {
                if (mNoTagsTextView!!.visibility == View.VISIBLE) {
                    mNoTagsTextView!!.visibility = View.GONE
                }
                mTags!!.add(tag)
                feedbackText = getString(R.string.tag_editor_add_feedback, tag, mPositiveText)
            } else {
                feedbackText = getString(R.string.tag_editor_add_feedback_existing, tag)
            }
            mTags!!.check(tag)
            mTagsArrayAdapter!!.sortData()
            mTagsArrayAdapter!!.notifyDataSetChanged()
            // Expand to reveal the newly added tag.
            mTagsArrayAdapter!!.filter.apply {
                setExpandTarget(tag)
                refresh()
            }

            // Show a snackbar to let the user know the tag was added successfully
            mDialog!!.view.findViewById<View>(R.id.tags_dialog_snackbar)
                .showSnackbar(feedbackText)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun getSearchView(): SearchView? {
        return mToolbarSearchView
    }

    companion object {
        private const val DIALOG_TYPE_KEY = "dialog_type"
        private const val CHECKED_TAGS_KEY = "checked_tags"
        private const val UNCHECKED_TAGS_KEY = "unchecked_tags"
        private const val ALL_TAGS_KEY = "all_tags"

        /**
         * The filter that constrains the inputted tag.
         * Space is not allowed in a tag. For UX of hierarchical tag, inputting a space will instead
         * insert "::" at the cursor. If there are already some colons in front of the cursor,
         * complete to 2 colons. For example:
         *   "tag"   -- input a space --> "tag::"
         *   "tag:"  -- input a space --> "tag::"
         *   "tag::" -- input a space --> "tag::"
         */
        private val addTagFilter =
            InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned?, destStart: Int, _: Int ->
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
                    previousColonsCnt =
                        if (char == ':') {
                            previousColonsCnt + 1
                        } else {
                            0
                        }
                }
                sb
            }
    }
}
