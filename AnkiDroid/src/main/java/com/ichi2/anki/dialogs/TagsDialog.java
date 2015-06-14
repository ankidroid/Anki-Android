package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class TagsDialog extends DialogFragment {
    public interface TagsDialogListener {
        void onPositive(List<String> selectedTags, int option);
    }

    public static final int TYPE_NONE = -1;
    public static final int TYPE_ADD_TAG = 0;
    public static final int TYPE_FILTER_BY_TAG = 1;
    public static final int TYPE_CUSTOM_STUDY_TAGS = 2;

    private static final String DIALOG_TYPE_KEY = "dialog_type";
    private static final String CHECKED_TAGS_KEY = "checked_tags";
    private static final String ALL_TAGS_KEY = "all_tags";

    private int mType = TYPE_NONE;
    private TreeSet<String> mCurrentTags;
    private ArrayList<String> mAllTags;

    private String mDialogTitle;
    private TagsDialogListener mTagsDialogListener = null;
    private TagsArrayAdapter mTagsArrayAdapter;
    private int mSelectedOption = -1;

    private RadioGroup mOptionsGroup;
    private CheckBox mCheckAllCheckBox;
    private EditText mAddFilterEditText;
    private ImageView mAddTagImageView;

    public static TagsDialog newInstance(int type, ArrayList<String> checked_tags,
                                            ArrayList<String> all_tags) {
        TagsDialog t = new TagsDialog();

        Bundle args = new Bundle();
        args.putInt(DIALOG_TYPE_KEY, type);
        args.putStringArrayList(CHECKED_TAGS_KEY, checked_tags);
        args.putStringArrayList(ALL_TAGS_KEY, all_tags);
        t.setArguments(args);

        return t;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        mType = getArguments().getInt(DIALOG_TYPE_KEY);

        mCurrentTags = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        mCurrentTags.addAll(getArguments().getStringArrayList(CHECKED_TAGS_KEY));

        mAllTags = new ArrayList<String>();
        mAllTags.addAll(getArguments().getStringArrayList(ALL_TAGS_KEY));

        setCancelable(true);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Resources res = getResources();

        View tagsDialogView = LayoutInflater.from(getActivity())
                .inflate(R.layout.tags_dialog, null, false);
        final RecyclerView tagsListRecyclerView = (RecyclerView) tagsDialogView.findViewById(R.id.tags_dialog_tags_list);
        tagsListRecyclerView.requestFocus();
        tagsListRecyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager tagsListLayout = new LinearLayoutManager(getActivity());
        tagsListRecyclerView.setLayoutManager(tagsListLayout);

        mTagsArrayAdapter = new TagsArrayAdapter();
        tagsListRecyclerView.setAdapter(mTagsArrayAdapter);

        mAddFilterEditText = (EditText) tagsDialogView.findViewById(R.id.tags_dialog_filter_edittext);
        mAddFilterEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                /* Filter the tag list based on user input */
                TagsArrayAdapter adapter = (TagsArrayAdapter) tagsListRecyclerView.getAdapter();
                adapter.getFilter().filter(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        InputFilter filter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart,
                                       int dend) {
                for (int i = start; i < end; i++) {
                    if (source.charAt(i) == ' ' || source.charAt(i) == ',') {
                        return "";
                    }
                }
                return null;
            }
        };
        mAddFilterEditText.setFilters(new InputFilter[]{filter});
        mAddFilterEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                /* Show a hint message about the add tag tick button when TextEdit gets focus if the tick button
                 * is actually shown, and the user has not previously learned how to add a new tag */
                SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(getActivity());
                if (mAddTagImageView.isShown() && hasFocus && !prefs.getBoolean("knowsHowToAddTag", false)) {
                    Toast.makeText(getActivity(), R.string.tag_editor_add_hint, Toast.LENGTH_SHORT).show();
                }
            }
        });
        mAddTagImageView = (ImageView) tagsDialogView.findViewById(R.id.tags_dialog_add_tag_imageview);
        mAddTagImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String tag = mAddFilterEditText.getText().toString();
                if (!TextUtils.isEmpty(tag)) {
                    mAddFilterEditText.setText("");
                    if (mCurrentTags.contains(tag)) {
                        return;
                    } else if (!mAllTags.contains(tag)) {
                        mAllTags.add(tag);
                        mTagsArrayAdapter.mTagsList.add(tag);
                        mTagsArrayAdapter.sortData();
                    }
                    mCurrentTags.add(tag);
                    mTagsArrayAdapter.notifyDataSetChanged();
                    // Show a toast to let the user know the tag was added successfully
                    Resources res = getResources();
                    Toast.makeText(getActivity(), res.getString(R.string.tag_editor_add_feedback, tag, res.getString(R.string.select)), Toast.LENGTH_LONG).show();
                    // Remember that the user has learned how to add a new tag
                    SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(getActivity());
                    prefs.edit().putBoolean("knowsHowToAddTag", true).commit();
                }
            }
        });

        mOptionsGroup = (RadioGroup) tagsDialogView.findViewById(R.id.tags_dialog_options_radiogroup);
        for (int i = 0; i < mOptionsGroup.getChildCount(); i++) {
            mOptionsGroup.getChildAt(i).setId(i);
        }
        mOptionsGroup.check(0);

        mSelectedOption = mOptionsGroup.getCheckedRadioButtonId();
        mOptionsGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                mSelectedOption = checkedId;
            }
        });

        adjustDialogFromType();

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .positiveText(res.getString(R.string.select))
                .negativeText(res.getString(R.string.dialog_cancel))
                .customView(tagsDialogView, false)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        mTagsDialogListener
                                .onPositive(new ArrayList<String>(mCurrentTags), mSelectedOption);
                    }
                });
        MaterialDialog dialog = builder.build();

        View customTitleView = LayoutInflater.from(getActivity())
                .inflate(R.layout.tags_dialog_title, null, false);
        TextView titleTV = (TextView) customTitleView.findViewById(R.id.tags_dialog_title_textview);
        titleTV.setText(mDialogTitle);
        mCheckAllCheckBox = (CheckBox) customTitleView.findViewById(R.id.tags_dialog_all_checkbox);
        mCheckAllCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean changed = false;
                for (String tag : mTagsArrayAdapter.mTagsList) {
                    if (mCheckAllCheckBox.isChecked() && !mCurrentTags.contains(tag)) {
                        mCurrentTags.add(tag);
                        changed = true;
                    } else if (!mCheckAllCheckBox.isChecked() && mCurrentTags.contains(tag)) {
                        mCurrentTags.remove(tag);
                        changed = true;
                    }
                }

                if (changed) {
                    mTagsArrayAdapter.notifyDataSetChanged();
                }
            }
        });
        mCheckAllCheckBox.setChecked(mCurrentTags.containsAll(mTagsArrayAdapter.mTagsList));
        LinearLayout titleLayout = (LinearLayout) dialog.getView().findViewById(R.id.titleFrame);
        titleLayout.removeAllViews();
        titleLayout.addView(customTitleView);
        titleLayout.setVisibility(View.VISIBLE);

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return dialog;
    }

    private void adjustDialogFromType() {
        switch (mType) {
            case TYPE_ADD_TAG:
                mDialogTitle = getResources().getString(R.string.card_details_tags);
                mOptionsGroup.setVisibility(View.GONE);
                mAddFilterEditText.setHint(R.string.add_new_filter_tags);
                break;
            default:
                mDialogTitle = getResources().getString(R.string.studyoptions_limit_select_tags);
                mAddTagImageView.setVisibility(View.GONE);
                mAddFilterEditText.setHint(R.string.filter_tags);
        }
    }

    public void setTagsDialogListener(TagsDialogListener selectedTagsListener) {
        mTagsDialogListener = selectedTagsListener;
    }

    public class TagsArrayAdapter extends  RecyclerView.Adapter<TagsArrayAdapter.ViewHolder> implements Filterable{
        public class ViewHolder extends RecyclerView.ViewHolder {
            private CheckedTextView mTagItemCheckedTextView;
            public ViewHolder(CheckedTextView ctv) {
                super(ctv);
                mTagItemCheckedTextView = ctv;
            }
        }

        public ArrayList<String> mTagsList;

        public  TagsArrayAdapter() {
            mTagsList = new ArrayList<String>();
            mTagsList.addAll(mAllTags);
            sortData();
        }

        public void sortData() {
            Collections.sort(mTagsList, new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    boolean lhs_checked = mCurrentTags.contains(lhs);
                    boolean rhs_checked = mCurrentTags.contains(rhs);
                    //priority for checked items.
                    return lhs_checked == rhs_checked ? lhs.compareToIgnoreCase(rhs) : lhs_checked ? -1 : 1;
                }
            });
        }

        @Override
        public TagsArrayAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                              int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tags_item_list_dialog, parent, false);

            ViewHolder vh = new ViewHolder((CheckedTextView) v.findViewById(R.id.tags_dialog_tag_item));
            vh.mTagItemCheckedTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    CheckedTextView ctv = (CheckedTextView) view;
                    ctv.toggle();
                    String tag = ctv.getText().toString();
                    if (ctv.isChecked() && !mCurrentTags.contains(tag)) {
                        mCurrentTags.add(tag);
                    } else if (!ctv.isChecked() && mCurrentTags.contains(tag)) {
                        mCurrentTags.remove(tag);
                    }
                }
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String tag = mTagsList.get(position);
            holder.mTagItemCheckedTextView.setText(tag);
            holder.mTagItemCheckedTextView.setChecked(mCurrentTags.contains(tag));
        }

        @Override
        public int getItemCount() {
            return mTagsList.size();
        }

        @Override
        public Filter getFilter() {
            return new TagsFilter();
        }

        /* Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026 */
        private class TagsFilter extends Filter {
            private ArrayList<String> mFilteredTags;
            private TagsFilter() {
                super();
                mFilteredTags = new ArrayList<String>();
            }

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                mFilteredTags.clear();
                final FilterResults filterResults = new FilterResults();
                if (constraint.length() == 0) {
                    mFilteredTags.addAll(mAllTags);
                } else {
                    final String filterPattern = constraint.toString().toLowerCase().trim();
                    for (String tag : mAllTags) {
                        if (tag.toLowerCase().startsWith(filterPattern)) {
                            mFilteredTags.add(tag);
                        }
                    }
                }

                filterResults.values = mFilteredTags;
                filterResults.count = mFilteredTags.size();
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                mTagsList.clear();
                mTagsList.addAll(mFilteredTags);
                sortData();
                notifyDataSetChanged();

                //if all tags being displayed are checked, then check the checkall checkbox.
                mCheckAllCheckBox.setChecked(mCurrentTags.containsAll(mTagsList));
            }
        }
    }
}
