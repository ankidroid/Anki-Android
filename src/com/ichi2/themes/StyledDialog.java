/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * based on custom Dialog windows by antoine vianey                                     *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.themes;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager.BadTokenException;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import java.util.ArrayList;
import java.util.List;

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
            super.show();
        } catch (BadTokenException e) {
            Log.e(AnkiDroidApp.TAG, "Could not show dialog: " + e);
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
        ((View) main.findViewById(R.id.contentPanel)).setVisibility(View.VISIBLE);
        Themes.setStyledDialogBackgrounds(main);
    }


    public void setTitle(String message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.alertTitle)).setText(message);
        Themes.setStyledDialogBackgrounds(main);
    }


    public void setMessage(String message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.message)).setText(message);
        ((View) main.findViewById(R.id.contentPanel)).setVisibility(View.VISIBLE);
        Themes.setStyledDialogBackgrounds(main);
    }


    public void setEnabled(boolean enabled) {
        mDoNotShow = !enabled;
    }


    private void setItems(int type, ListView listview, String[] values, int checkedItem, boolean[] checked,
            DialogInterface.OnClickListener listener) {
        mListView = listview;
        mItemList = new ArrayList<String>();
        for (String titel : values) {
            mItemList.add(titel);
        }
        mListener = listener;
        if (type == 3) {
            mListView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mListener.onClick(StyledDialog.this, position);
                }
            });
        } else {
            mListView.setOnItemClickListener(new OnItemClickListener() {
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


    public Button getButton(int which) {
        switch (which) {
            case Dialog.BUTTON_POSITIVE:
                return (Button) super.getWindow().getDecorView().findViewById(R.id.button1);
            case Dialog.BUTTON_NEGATIVE:
                return (Button) super.getWindow().getDecorView().findViewById(R.id.button2);
            case Dialog.BUTTON_NEUTRAL:
                return (Button) super.getWindow().getDecorView().findViewById(R.id.button3);
            default:
                return null;
        }
    }

    public void setButtonOnClickListener(int which, OnClickListener listener) {
    	getButton(which).setOnClickListener(new OnClickForwarder(StyledDialog.this, which, listener));
    }


    public void addMultiChoiceItems(String value, boolean checked) {
        mItemList.add(0, value);
        mListView.setItemChecked(0, checked);
        boolean[] newChecked = new boolean[mItemList.size()];
        newChecked[0] = checked;
        for (int i = 1; i < mItemList.size(); i++) {
            boolean c = mCheckedItems[i - 1];
            mListView.setItemChecked(i, c);
            newChecked[i] = c;
        }
        mCheckedItems = newChecked;
        mListAdapter.notifyDataSetChanged();
    }


    public void setMultiChoiceItems(String[] values, boolean[] checked, DialogInterface.OnClickListener listener) {
        View main = super.getWindow().getDecorView();
        mCheckedItems = checked;
        ((View) main.findViewById(R.id.listViewPanel)).setVisibility(View.VISIBLE);
        setItems(3, (ListView) super.getWindow().getDecorView().findViewById(R.id.listview), values, 0, mCheckedItems,
                listener);
    }


    public void setSingleChoiceItems(String[] values, int checked, DialogInterface.OnClickListener listener) {
        View main = super.getWindow().getDecorView();
        ((View) main.findViewById(R.id.listViewPanel)).setVisibility(View.VISIBLE);
        setItems(2, (ListView) super.getWindow().getDecorView().findViewById(R.id.listview), values, 0, null, listener);
    }


    public void setItems(String[] values, DialogInterface.OnClickListener listener) {
        View main = super.getWindow().getDecorView();
        ((View) main.findViewById(R.id.listViewPanel)).setVisibility(View.VISIBLE);
        setItems(1, (ListView) super.getWindow().getDecorView().findViewById(R.id.listview), values, 0, null, listener);
    }


    public void changeListItem(int position, String text) {
        mItemList.remove(position);
        mItemList.add(position, text);
        mListAdapter.notifyDataSetChanged();
    }

    public static class Builder {

        private Context context;
        private String title;
        private String message;
        private int messageSize = 0;
        private String positiveButtonText;
        private String negativeButtonText;
        private String neutralButtonText;
        private View contentView;
        private int bottomMargin = 0;
        private boolean brightViewBackground = false;
        private int icon = 0;

        private DialogInterface.OnClickListener positiveButtonClickListener;
        private DialogInterface.OnClickListener negativeButtonClickListener;
        private DialogInterface.OnClickListener neutralButtonClickListener;
        private DialogInterface.OnCancelListener cancelListener;
        private DialogInterface.OnDismissListener dismissListener;
        private boolean cancelable = true;

        private String[] itemTitels;
        private int checkedItem;
        private boolean[] multipleCheckedItems;
        private int listStyle = 0;
        private DialogInterface.OnClickListener itemClickListener;


        public Builder(Context context) {
            this.context = context;
        }


        /**
         * Set the Dialog message from String
         * 
         * @param title
         * @return
         */
        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }


        public Builder setMessage(String message, int size) {
            this.message = message;
            this.messageSize = size;
            return this;
        }


        /**
         * Set the Dialog message from resource
         * 
         * @param title
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
            return setView(v, isSingleView, false);
        }


        public Builder setView(View v, boolean isSingleView, boolean bright) {
            this.contentView = v;
            this.bottomMargin = isSingleView ? 5 : 0;
            this.brightViewBackground = bright;
            return this;
        }


        /**
         * Set the positive button resource and it's listener
         * 
         * @param positiveButtonText
         * @param listener
         * @return
         */
        public Builder setPositiveButton(int positiveButtonText, DialogInterface.OnClickListener listener) {
            this.positiveButtonText = (String) context.getText(positiveButtonText);
            this.positiveButtonClickListener = listener;
            return this;
        }


        /**
         * Set the positive button text and it's listener
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
         * Set the negative button resource and it's listener
         * 
         * @param negativeButtonText
         * @param listener
         * @return
         */
        public Builder setNegativeButton(int negativeButtonText, DialogInterface.OnClickListener listener) {
            this.negativeButtonText = (String) context.getText(negativeButtonText);
            this.negativeButtonClickListener = listener;
            return this;
        }


        /**
         * Set the negative button text and it's listener
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


        /**
         * Set the neutral button resource and it's listener
         * 
         * @param neutralButtonText
         * @param listener
         * @return
         */
        public Builder setNeutralButton(int neutralButtonText, DialogInterface.OnClickListener listener) {
            this.neutralButtonText = (String) context.getText(neutralButtonText);
            this.neutralButtonClickListener = listener;
            return this;
        }


        /**
         * Set the neutral button text and it's listener
         * 
         * @param neutralButtonText
         * @param listener
         * @return
         */
        public Builder setNeutralButton(String neutralButtonText, DialogInterface.OnClickListener listener) {
            this.neutralButtonText = neutralButtonText;
            this.neutralButtonClickListener = listener;
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


        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }


        public Builder setItems(String[] values, DialogInterface.OnClickListener listener) {
            this.itemTitels = values;
            this.itemClickListener = listener;
            this.listStyle = 1;
            return this;
        }


        public Builder setSingleChoiceItems(String[] values, int checked, DialogInterface.OnClickListener listener) {
            this.itemTitels = values;
            this.checkedItem = checked;
            this.itemClickListener = listener;
            this.listStyle = 2;
            return this;
        }


        public Builder setMultiChoiceItems(String[] values, boolean[] checked, DialogInterface.OnClickListener listener) {
            this.itemTitels = values;
            this.multipleCheckedItems = checked;
            this.itemClickListener = listener;
            this.listStyle = 3;
            return this;
        }


        /**
         * Create the styled dialog
         */
        public StyledDialog create() {
            final StyledDialog dialog = new StyledDialog(context);
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View layout = inflater.inflate(R.layout.styled_dialog, null);
            dialog.addContentView(layout, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

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
                Button button1 = (Button) layout.findViewById(R.id.button1);
                button1.setText(positiveButtonText);
                button1.setOnClickListener(new OnClickForwarder(dialog, DialogInterface.BUTTON_POSITIVE,
                        positiveButtonClickListener));
                numberOfButtons++;
            } else {
                layout.findViewById(R.id.button1).setVisibility(View.GONE);
            }
            if (negativeButtonText != null) {
                Button button2 = (Button) layout.findViewById(R.id.button2);
                button2.setText(negativeButtonText);
                button2.setOnClickListener(new OnClickForwarder(dialog, DialogInterface.BUTTON_NEGATIVE,
                        negativeButtonClickListener));
                numberOfButtons++;
            } else {
                layout.findViewById(R.id.button2).setVisibility(View.GONE);
            }
            if (neutralButtonText != null) {
                Button button3 = (Button) layout.findViewById(R.id.button3);
                button3.setText(neutralButtonText);
                button3.setOnClickListener(new OnClickForwarder(dialog, DialogInterface.BUTTON_NEUTRAL,
                        neutralButtonClickListener));
                numberOfButtons++;
            } else {
                layout.findViewById(R.id.button3).setVisibility(View.GONE);
            }
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
            if (itemTitels != null) {
                dialog.setItems(listStyle, (ListView) layout.findViewById(R.id.listview), itemTitels, checkedItem,
                        multipleCheckedItems, itemClickListener);
                // ((View) layout.findViewById(R.id.titleDivider)).setVisibility(View.GONE);
            } else {
                ((View) layout.findViewById(R.id.listViewPanel)).setVisibility(View.GONE);
            }

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

            // set background
            try {
                Themes.setStyledDialogBackgrounds(layout, numberOfButtons, brightViewBackground);
            } catch (OutOfMemoryError e) {
                Log.e(AnkiDroidApp.TAG, "StyledDialog - Dialog could not be created: " + e);
                Themes.showThemedToast(context, context.getResources().getString(R.string.error_insufficient_memory),
                        false);
                return null;
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
