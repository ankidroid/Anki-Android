package com.ichi2.themes;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;

public class StyledDialog extends Dialog {

    private Context mContext;
    private List<String> mItemList;
    private boolean[] mCheckedItems;
    private ArrayAdapter<String> mListAdapter;
    private OnClickListener mListener;
    private ListView mListView;
    private boolean mDoNotShow = false;


    public StyledDialog(Context context) {
        super(context, R.style.StyledDialog);
        mContext = context;
    }

    @Override
    public void show() {
        try {
            setCanceledOnTouchOutside(false);
            super.show();
        } catch (WindowManager.BadTokenException e) {
            Timber.e(e, "Could not show dialog");
        }
    }


    // @Override On Android 1.5 this is not Override
    public void onAttachedToWindow() {
        if (mDoNotShow) {
            this.dismiss();
        }
    }


    public void setMessage(CharSequence message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.message)).setText(message);
        main.findViewById(R.id.contentPanel).setVisibility(View.VISIBLE);
    }


    public void setTitle(String message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.alertTitle)).setText(message);
    }


    public void setMessage(String message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.message)).setText(message);
        main.findViewById(R.id.contentPanel).setVisibility(View.VISIBLE);
    }


    public void setEnabled(boolean enabled) {
        mDoNotShow = !enabled;
    }


    private void setItems(int type, ListView listview, String[] values, int checkedItem, boolean[] checked,
                          DialogInterface.OnClickListener listener) {
        mListView = listview;
        mItemList = new ArrayList<String>();
        Collections.addAll(mItemList, values);
        mListener = listener;
        if (type == 3) {
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mListener.onClick(StyledDialog.this, position);
                    adjustSelectAllCheckBox();
                }
            });
        } else {
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mListener.onClick(StyledDialog.this, position);
                    StyledDialog.this.dismiss();
                }
            });
        }
        switch (type) {
            case 1:
                mListAdapter = new ArrayAdapter<String>(mContext, R.layout.select_dialog_nochoice, 0, mItemList);
                mListView.setAdapter(mListAdapter);
                mListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
                break;
            case 2:
                mListAdapter = new ArrayAdapter<String>(mContext, R.layout.select_dialog_singlechoice, 0, mItemList);
                mListView.setAdapter(mListAdapter);
                mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                mListView.setItemChecked(checkedItem, true);
                break;
            case 3:
                mListAdapter = new ArrayAdapter<String>(mContext, R.layout.select_dialog_multichoice, 0, mItemList);
                mListView.setAdapter(mListAdapter);
                mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                for (int i = 0; i < checked.length; i++) {
                    listview.setItemChecked(i, checked[i]);
                }
                break;
        }
    }


    public void setMultiChoiceItems(String[] values, boolean[] checked, DialogInterface.OnClickListener listener,
                                    CompoundButton.OnCheckedChangeListener selectAllListener) {
        View main = super.getWindow().getDecorView();
        mCheckedItems = checked;
        ((View) main.findViewById(R.id.listViewPanel)).setVisibility(View.VISIBLE);
        setItems(3, (ListView) super.getWindow().getDecorView().findViewById(R.id.listview), values, 0, mCheckedItems,
                listener);

        if (selectAllListener != null) {
            CheckBox selectAll = (CheckBox) main.findViewById(R.id.SelectAllCheckbox);
            selectAll.setVisibility(View.VISIBLE);
            selectAll.setTag(selectAllListener);
            adjustSelectAllCheckBox();
        }
    }


    public void setItemListChecked(boolean checked) {
        for (int i = 0; i < mListView.getCount(); i++) {
            mListView.setItemChecked(i, checked);
        }
    }


    public ArrayList<String> getCheckedItems() {
        ArrayList<String> selecteds = new ArrayList<String>();
        if (mItemList != null) {
            updateCheckedItems();
            for (int i = 0; i < mItemList.size(); i++) {
                if (mCheckedItems[i]) {
                    selecteds.add(mItemList.get(i));
                }
            }
        }
        return selecteds;
    }


    public void updateCheckedItems() {
        if (mCheckedItems == null) {
            mCheckedItems = new boolean[mItemList.size()];
        }
        for (int i = 0; i < mItemList.size(); i++) {
            String item = mItemList.get(i);
            boolean is_checked = false;
            boolean is_showing = false;
            for (int j = 0; j < mListView.getCount(); j++) {
                String item_in_view = (String) mListView.getItemAtPosition(j);
                if (item.equals(item_in_view)) {
                    is_showing = true;
                    is_checked = mListView.isItemChecked(j);
                    break;
                }
            }
            if (is_showing) {
                mCheckedItems[i] = is_checked;
            }
        }
    }


    public void filterList(String str) {
        filterList(str, null);
    }


    public void filterList(String str, final Filter.FilterListener listener) {
        updateCheckedItems();
        mListAdapter.getFilter().filter(str, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                for (int i = 0; i < mListView.getCount(); i++) {
                    String item = (String) mListView.getItemAtPosition(i);
                    mListView.setItemChecked(i, mCheckedItems[mItemList.indexOf(item)]);
                }
                adjustSelectAllCheckBox();
            }
        });
    }


    private void adjustSelectAllCheckBox() {
        boolean check = true;
        for (int i = 0; i < mListView.getCount(); i++) {
            if (!mListView.isItemChecked(i)) {
                check = false;
                break;
            }
        }
        CheckBox selectAll = (CheckBox) findViewById(R.id.SelectAllCheckbox);
        selectAll.setOnCheckedChangeListener(null);
        selectAll.setChecked(check);
        selectAll.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener) selectAll.getTag());
    }

    public static class Builder {

        private Context context;
        private String title;
        private String message;
        private int messageSize = 0;
        private String positiveButtonText;
        private String negativeButtonText;
        private View contentView;
        private int bottomMargin = 0;
        private int icon = 0;

        private DialogInterface.OnClickListener positiveButtonClickListener;
        private DialogInterface.OnClickListener negativeButtonClickListener;
        private DialogInterface.OnCancelListener cancelListener;
        private DialogInterface.OnDismissListener dismissListener;
        private CompoundButton.OnCheckedChangeListener selectAllListener;
        private boolean cancelable = true;

        private int listStyle = 0;
        private boolean mShowFilterEditText = false;


        public Builder(Context context) {
            this.context = context;
        }


        /**
         * Set the Dialog message from String
         *
         * @param message
         * @return
         */
        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        /**
         * Set the Dialog message from resource
         *
         * @param message
         * @return
         */
        public Builder setMessage(int message) {
            this.message = (String) context.getText(message);
            return this;
        }


        public Builder setIcon(int icon) {
            this.icon = icon;
            return this;
        }


        /**
         * Set the Dialog title from resource
         *
         * @param title
         * @return
         */
        public Builder setTitle(int title) {
            this.title = (String) context.getText(title);
            return this;
        }


        /**
         * Set the Dialog title from String
         *
         * @param title
         * @return
         */
        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }


        /**
         * Set a custom content view for the Dialog. If a message is set, the contentView is not added to the Dialog...
         *
         * @param v
         * @return
         */
        public Builder setContentView(View v) {
            this.contentView = v;
            return this;
        }


        public Builder setView(View v) {
            return setView(v, false);
        }


        public Builder setView(View v, boolean isSingleView) {
            this.contentView = v;
            this.bottomMargin = isSingleView ? 5 : 0;
            return this;
        }


        /**
         * Set the positive button text and its listener
         *
         * @param positiveButtonText
         * @param listener
         * @return
         */
        public Builder setPositiveButton(String positiveButtonText, DialogInterface.OnClickListener listener) {
            this.positiveButtonText = positiveButtonText;
            this.positiveButtonClickListener = listener;
            return this;
        }



        /**
         * Set the negative button text and its listener
         *
         * @param negativeButtonText
         * @param listener
         * @return
         */
        public Builder setNegativeButton(String negativeButtonText, DialogInterface.OnClickListener listener) {
            this.negativeButtonText = negativeButtonText;
            this.negativeButtonClickListener = listener;
            return this;
        }


        public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) {
            this.cancelListener = listener;
            return this;
        }


        public Builder setOnDismissListener(DialogInterface.OnDismissListener listener) {
            this.dismissListener = listener;
            return this;
        }


        public Builder setSelectAllListener(CompoundButton.OnCheckedChangeListener listener) {
            this.selectAllListener = listener;
            return this;
        }


        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }


        public Builder setShowFilterTags(boolean show) {
            mShowFilterEditText = show;
            return this;
        }


        /**
         * Create the styled dialog
         */
        public StyledDialog create() {
            final StyledDialog dialog = new StyledDialog(context);
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View layout = inflater.inflate(R.layout.styled_dialog, null);
            dialog.addContentView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // set title
            if (title != null && title.length() > 0) {
                ((TextView) layout.findViewById(R.id.alertTitle)).setText(title);
                if (icon != 0) {
                    ((ImageView) layout.findViewById(R.id.icon)).setImageResource(icon);
                } else {
                    layout.findViewById(R.id.icon).setVisibility(View.GONE);
                }
            } else {
                layout.findViewById(R.id.topPanel).setVisibility(View.GONE);
                layout.findViewById(R.id.titleDivider).setVisibility(View.GONE);
            }

            // set buttons
            int numberOfButtons = 0;
            if (positiveButtonText != null) {
                Button positiveButton = (Button) layout.findViewById(R.id.positive_button);
                positiveButton.setText(positiveButtonText);
                positiveButton.setOnClickListener(new OnClickForwarder(dialog, DialogInterface.BUTTON_POSITIVE,
                        positiveButtonClickListener));
                numberOfButtons++;
            } else {
                layout.findViewById(R.id.positive_button).setVisibility(View.GONE);
            }
            if (negativeButtonText != null) {
                Button negativeButton = (Button) layout.findViewById(R.id.negative_button);
                negativeButton.setText(negativeButtonText);
                negativeButton.setOnClickListener(new OnClickForwarder(dialog, DialogInterface.BUTTON_NEGATIVE,
                        negativeButtonClickListener));
                numberOfButtons++;
            } else {
                layout.findViewById(R.id.negative_button).setVisibility(View.GONE);
            }
            layout.findViewById(R.id.neutral_button).setVisibility(View.GONE);
            if (numberOfButtons == 0) {
                layout.findViewById(R.id.buttonPanel).setVisibility(View.GONE);
            }

            dialog.setCancelable(cancelable);
            dialog.setOnCancelListener(cancelListener);

            dialog.setOnDismissListener(dismissListener);

            // set the message
            if (message != null) {
                TextView tv = (TextView) layout.findViewById(R.id.message);
                tv.setText(message);
                if (messageSize != 0) {
                    tv.setTextSize(messageSize * context.getResources().getDisplayMetrics().scaledDensity);
                }
            } else {
                ((LinearLayout) layout.findViewById(R.id.contentPanel)).setVisibility(View.GONE);
            }

            // set single and multiple choice listview
            ((View) layout.findViewById(R.id.listViewPanel)).setVisibility(View.GONE);

            // set a custom view
            if (contentView != null) {
                FrameLayout frame = (FrameLayout) layout.findViewById(R.id.custom);
                float factor = context.getResources().getDisplayMetrics().density;
                frame.setPadding((int) (2 * factor), (int) ((5 - bottomMargin) * factor), (int) (2 * factor),
                        (int) (bottomMargin * factor));
                frame.removeAllViews();
                frame.addView(contentView);
            } else {
                ((View) layout.findViewById(R.id.customPanel)).setVisibility(View.GONE);
            }

            if (this.listStyle == 3 && selectAllListener != null) {
                CheckBox selectAll = (CheckBox) layout.findViewById(R.id.SelectAllCheckbox);
                selectAll.setVisibility(View.VISIBLE);
                selectAll.setTag(selectAllListener);
                dialog.adjustSelectAllCheckBox();
            }

            if (mShowFilterEditText) {
                final EditText filterTags = (EditText) layout.findViewById(R.id.filterTags);
                filterTags.setVisibility(View.VISIBLE);
                filterTags.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        dialog.filterList(s.toString(), new Filter.FilterListener() {
                            @Override
                            public void onFilterComplete(int count) {
                                dialog.adjustSelectAllCheckBox();
                            }
                        });
                    }


                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }


                    @Override
                    public void afterTextChanged(Editable s) {
                    }
                });
                dialog.setOnDismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        filterTags.setText("");
                        if (dismissListener != null) {
                            dismissListener.onDismiss(dialog);
                        }
                    }
                });
            }

            dialog.setContentView(layout);
            return dialog;
        }


        public void show() {
            create().show();
        }

    }

    private static class OnClickForwarder implements View.OnClickListener {

        private final DialogInterface mDialog;
        private final int mWhich;
        private final DialogInterface.OnClickListener mListener;


        public OnClickForwarder(DialogInterface dialog, int which, DialogInterface.OnClickListener listener) {
            mDialog = dialog;
            mWhich = which;
            mListener = listener;
        }


        @Override
        public void onClick(View view) {
            if (mListener != null) {
                mListener.onClick(mDialog, mWhich);
            }
            mDialog.dismiss();
        }
    }
}

