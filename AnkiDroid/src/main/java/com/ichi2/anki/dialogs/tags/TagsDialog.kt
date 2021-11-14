//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs.tags

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showSnackbar
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import java.util.*

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

    private var mType: DialogType? = null
    private var mTags: TagsList? = null
    private var mPositiveText: String? = null
    private var mDialogTitle: String? = null
    private var mTagsArrayAdapter: TagsArrayAdapter? = null
    private var mSelectedOption = -1
    private var mToolbarSearchView: SearchView? = null
    private var mToolbarSearchItem: MenuItem? = null
    private var mNoTagsTextView: TextView? = null
    private var mTagsListRecyclerView: RecyclerView? = null
    private var mDialog: MaterialDialog? = null
    private val mListener: TagsDialogListener?

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
    fun withArguments(type: DialogType, checkedTags: List<String?>, allTags: List<String?>): TagsDialog {
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
        allTags: List<String?>
    ): TagsDialog {
        var args = this.arguments
        if (args == null) {
            args = Bundle()
        }
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
        mType = DialogType.values()[requireArguments().getInt(DIALOG_TYPE_KEY)]
        mTags = TagsList(
            requireArguments().getStringArrayList(ALL_TAGS_KEY)!!,
            requireArguments().getStringArrayList(CHECKED_TAGS_KEY)!!,
            requireArguments().getStringArrayList(UNCHECKED_TAGS_KEY)
        )
        isCancelable = true
    }

    private val tagsDialogListener: TagsDialogListener
        get() = mListener
            ?: TagsDialogListener.createFragmentResultSender(parentFragmentManager)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        @SuppressLint("InflateParams") val tagsDialogView = LayoutInflater.from(activity).inflate(R.layout.tags_dialog, null, false)
        mTagsListRecyclerView = tagsDialogView.findViewById(R.id.tags_dialog_tags_list)
        val tagsListRecyclerView: RecyclerView? = mTagsListRecyclerView
        tagsListRecyclerView?.requestFocus()
        val tagsListLayout: RecyclerView.LayoutManager = LinearLayoutManager(activity)
        tagsListRecyclerView?.layoutManager = tagsListLayout
        mTagsArrayAdapter = TagsArrayAdapter(mTags!!)
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
        mSelectedOption = optionsGroup.checkedRadioButtonId
        optionsGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int -> mSelectedOption = checkedId }
        if (mType == DialogType.EDIT_TAGS) {
            mDialogTitle = resources.getString(R.string.card_details_tags)
            optionsGroup.visibility = View.GONE
            mPositiveText = getString(R.string.dialog_ok)
        } else {
            mDialogTitle = resources.getString(R.string.studyoptions_limit_select_tags)
            mPositiveText = getString(R.string.select)
        }
        adjustToolbar(tagsDialogView)
        val builder = MaterialDialog.Builder(requireActivity())
            .positiveText(mPositiveText!!)
            .negativeText(R.string.dialog_cancel)
            .customView(tagsDialogView, false)
            .onPositive { _: MaterialDialog?, _: DialogAction? -> tagsDialogListener.onSelectedTags(mTags!!.copyOfCheckedTagList(), mTags!!.copyOfIndeterminateTagList(), mSelectedOption) }
        mDialog = builder.build()
        val dialog: MaterialDialog? = mDialog
        resizeWhenSoftInputShown(dialog?.window!!)
        return dialog
    }

    private fun adjustToolbar(tagsDialogView: View) {
        val toolbar: Toolbar = tagsDialogView.findViewById(R.id.tags_dialog_toolbar)
        toolbar.title = mDialogTitle
        toolbar.inflateMenu(R.menu.tags_dialog_menu)

        // disallow inputting the 'space' character
        val addTagFilter = InputFilter { source: CharSequence, start: Int, end: Int, _: Spanned?, _: Int, _: Int ->
            var i = start
            while (i < end) {
                if (source[i] == ' ') {
                    return@InputFilter ""
                }
                i++
            }
            null
        }
        val toolbarAddItem = toolbar.menu.findItem(R.id.tags_dialog_action_add)
        toolbarAddItem.setOnMenuItemClickListener {
            val query = mToolbarSearchView!!.query.toString()
            if (mToolbarSearchItem!!.isActionViewExpanded && !TextUtils.isEmpty(query)) {
                addTag(query)
                mToolbarSearchView!!.setQuery("", true)
            } else {
                val addTagBuilder = MaterialDialog.Builder(requireActivity())
                    .title(getString(R.string.add_tag))
                    .negativeText(R.string.dialog_cancel)
                    .positiveText(R.string.dialog_ok)
                    .inputType(InputType.TYPE_CLASS_TEXT)
                    .input(R.string.tag_name, R.string.empty_string) { _: MaterialDialog?, input: CharSequence -> addTag(input.toString()) }
                val addTagDialog = addTagBuilder.build()
                val inputET = requireDialogInputEditText(addTagDialog)
                inputET.filters = arrayOf(addTagFilter)
                addTagDialog.show()
            }
            true
        }
        mToolbarSearchItem = toolbar.menu.findItem(R.id.tags_dialog_action_filter)
        val toolbarSearchItem: MenuItem? = mToolbarSearchItem
        mToolbarSearchView = toolbarSearchItem?.actionView as SearchView
        val queryET = mToolbarSearchView!!.findViewById<EditText>(R.id.search_src_text)
        queryET.filters = arrayOf(addTagFilter)
        mToolbarSearchView!!.queryHint = getString(R.string.filter_tags)
        mToolbarSearchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                mToolbarSearchView!!.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                val adapter = mTagsListRecyclerView!!.adapter as TagsArrayAdapter?
                adapter!!.filter.filter(newText)
                return true
            }
        })
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
    private fun requireDialogInputEditText(dialog: MaterialDialog): EditText {
        return dialog.inputEditText
            ?: throw IllegalStateException("MaterialDialog $dialog does not have an input edit text.")
    }

    @VisibleForTesting
    fun addTag(tag: String?) {
        if (!TextUtils.isEmpty(tag)) {
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
            // Show a snackbar to let the user know the tag was added successfully
            showSnackbar(
                requireActivity(), feedbackText, false, -1, null,
                mDialog!!.view.findViewById(R.id.tags_dialog_snackbar), null
            )
        }
    }

    companion object {
        private const val DIALOG_TYPE_KEY = "dialog_type"
        private const val CHECKED_TAGS_KEY = "checked_tags"
        private const val UNCHECKED_TAGS_KEY = "unchecked_tags"
        private const val ALL_TAGS_KEY = "all_tags"
    }
}
