package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class TagsDialog extends DialogFragment implements OnDismissListener, OnCancelListener {

    public interface TagsDialogListener {
        public void onPositive(List<String> selectedTags, int option);
    }

    private static final String DIALOG_TYPE_KEY = "dialog_type";
    private static final String CHECKED_TAGS_KEY = "checked_tags";
    private static final String ALL_TAGS_KEY = "all_tags";
    
    public static final int TYPE_NONE = -1;
    public static final int TYPE_ADD_TAG = 0;
    public static final int TYPE_FILTER_BY_TAG = 1;
    public static final int TYPE_CUSTOM_STUDY_TAGS = 2;
    
    private int mType = TYPE_NONE;
    private TreeSet<String> mCurrentTags;
    private ArrayList<String> mAllTags;

    private ImageView mAddTagIV;
    private String mTitle = null;
    private DialogInterface.OnClickListener mNegativeButtonListener = null;
    private DialogInterface.OnCancelListener mOnCancelListener = null;
    private DialogInterface.OnDismissListener mOnDismissListener = null;
    private TagsDialogListener mTagsDialogListener = null;

    private StyledDialog mStyledDialog = null;
    private int mSelectedOption = -1;
    
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
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (mStyledDialog == null) {
            mStyledDialog = createBuilder().create();
            mStyledDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            actualizeCurrTagDialog();
        }
        return mStyledDialog;
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
    
    private StyledDialog.Builder createBuilder() {
        View customView = null;
        boolean showFilterTags = false;
        switch(mType) {
            case TYPE_ADD_TAG:
                customView = addDialogContent();
                break;
            case TYPE_FILTER_BY_TAG:
                customView = filterByTagContent();
                showFilterTags = true;
                break;
            case TYPE_CUSTOM_STUDY_TAGS:
                customView = filterByTagContent();
                showFilterTags = true;
                break;                
            default:
                break;
        }

        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(getActivity());
        builder.setTitle(mTitle);
        builder.setPositiveButton(res.getString(R.string.select), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mCurrentTags.addAll(mStyledDialog.getCheckedItems());
                mTagsDialogListener.onPositive(new ArrayList<String>(mCurrentTags), mSelectedOption);
            }
        });
        builder.setNegativeButton(res.getString(R.string.cancel), mNegativeButtonListener);
        builder.setOnCancelListener(mOnCancelListener);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mCurrentTags.clear();
                mStyledDialog.updateCheckedItems();
                if (mOnDismissListener != null) {
                    mOnDismissListener.onDismiss(mStyledDialog);
                }
            }
        });
        builder.setSelectAllListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mStyledDialog.setItemListChecked(isChecked);
                mCurrentTags.clear();
                mCurrentTags.addAll(mStyledDialog.getCheckedItems());
            }
        });
        if (customView != null) {
            builder.setView(customView, false, true);
        }
        builder.setShowFilterTags(showFilterTags);

        return builder;
    }
    
    private View filterByTagContent() {
        mTitle = getResources().getString(R.string.studyoptions_limit_select_tags);

        Context context = getActivity();
        Resources res = context.getResources();

        RadioGroup rg = new RadioGroup(context);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        String[] text = res.getStringArray(R.array.cards_for_tag_filtered_deck_labels);
        final RadioButton[] radioButtons = new RadioButton[text.length];
        int height = res.getDrawable(R.drawable.white_btn_radio).getIntrinsicHeight();
        //workaround for text overlapping radiobutton. 
        //It appears that when API Level is <= 16, the text would overlap the radio button, hence the
        //addition of spaces.
        String spaces = "";
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            spaces = "         ";
        }
        for (int i = 0; i < radioButtons.length; i++) {
            radioButtons[i] = new RadioButton(context);
            radioButtons[i].setClickable(true);
            radioButtons[i].setText(spaces + text[i]);
            radioButtons[i].setSingleLine();
            radioButtons[i].setBackgroundDrawable(null);
            radioButtons[i].setGravity(Gravity.CENTER_VERTICAL);
            rg.addView(radioButtons[i], lp);
        }

        mSelectedOption = 0;
        radioButtons[mSelectedOption].setChecked(true);
        rg.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, height));
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int checked = group.getCheckedRadioButtonId();
                for (int i = 0; i < radioButtons.length; i++) {
                    if (group.getChildAt(i).getId() == checked) {
                        mSelectedOption = i;
                        break;
                    }
                }
            }
        });
        rg.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, height));
        return rg;
    }
    
    private View addDialogContent() {
        mTitle = getResources().getString(R.string.card_details_tags);

        final EditText addFilterTagET = new EditText(getActivity());
        addFilterTagET.setHint(R.string.add_new_filter_tags);
        addFilterTagET.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        mStyledDialog.filterList(s.toString());
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
        addFilterTagET.setFilters(new InputFilter[] { filter });
        addFilterTagET.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                /* Show a hint message about the add tag tick button when TextEdit gets focus if the tick button
                 * is actually shown, and the user has not previously learned how to add a new tag */
                SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(getActivity());
                if(mAddTagIV.isShown() && hasFocus && !prefs.getBoolean("knowsHowToAddTag",false)){
                    Toast.makeText(getActivity(), R.string.tag_editor_add_hint , Toast.LENGTH_SHORT).show();
                }
               }
            });

        mAddTagIV = new ImageView(getActivity());
        mAddTagIV.setImageResource(R.drawable.ic_addtag);
        mAddTagIV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tag = addFilterTagET.getText().toString();
                if (!TextUtils.isEmpty(tag)) {
                    if (mCurrentTags.contains(tag)) {
                        addFilterTagET.setText("");
                        return;
                    }
                    if (!mAllTags.contains(tag)) {
                        mAllTags.add(tag);
                    }
                    mCurrentTags.add(tag);
                    addFilterTagET.setText("");
                    actualizeCurrTagDialog();
                    // Show a toast to let the user know the tag was added successfully
                    Resources res = getResources();
                    Toast.makeText(getActivity(), res.getString(R.string.tag_editor_add_feedback, tag, res.getString(R.string.select)), Toast.LENGTH_LONG).show();                    
                    // Remember that the user has learned how to add a new tag
                    SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(getActivity());
                    prefs.edit().putBoolean("knowsHowToAddTag", true).commit();
                }
            }
        });

        FrameLayout frame = new FrameLayout(getActivity());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        params.rightMargin = 10;
        mAddTagIV.setLayoutParams(params);
        frame.addView(addFilterTagET);
        frame.addView(mAddTagIV);

        mOnDismissListener = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                addFilterTagET.setText("");
            }
        };
        return frame;
    }

    private boolean []checkedTags() {
        boolean []checked = new boolean[mAllTags.size()];
        for (int i = 0; i < checked.length; i++) {
            checked[i] = mCurrentTags.contains(mAllTags.get(i));
        }        
        
        return checked;
    }

    /**
     * Set the item list and checked items for the current dialog.
     * @param allTags
     * @param checkedTags
     */
    public void actualizeCurrTagDialog() {
        for (String tag : mCurrentTags) {
            if (!mAllTags.contains(tag)) {
                mAllTags.add(tag);
            }
        }
        Collections.sort(mAllTags, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareToIgnoreCase(rhs);
            }
        });
        String []tags = new String[mAllTags.size()];
        for (int i = 0; i < tags.length; i++) {
            tags[i] = mAllTags.get(i);
        }
        mStyledDialog.setMultiChoiceItems(tags, checkedTags(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String tag = mStyledDialog.getItemList().get(which);
                if (mCurrentTags.contains(tag)) {
                    mCurrentTags.remove(tag);
                } else {
                    mCurrentTags.add(tag);
                }
            }
        }, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mStyledDialog.setItemListChecked(isChecked);
                mCurrentTags.clear();
                mCurrentTags.addAll(mStyledDialog.getCheckedItems());
            }
        });
    }

    public void setTagsDialogListener(TagsDialogListener selectedTagsListener) {
        mTagsDialogListener = selectedTagsListener;
    }
}
