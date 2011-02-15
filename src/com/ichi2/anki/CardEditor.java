/***************************************************************************************
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

package com.ichi2.anki;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.Fact.Field;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * Allows the user to edit a fact, for instance if there is a typo.
 *
 * A card is a presentation of a fact, and has two sides: a question and an answer.
 * Any number of fields can appear on each side. When you add a fact to Anki, cards
 * which show that fact are generated. Some models generate one card, others generate
 * more than one.
 *
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class CardEditor extends Activity {

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private LinearLayout mFieldsLayoutContainer;
    private Button mSave;
    private Button mCancel;

    private Card mEditorCard;

    private LinkedList<FieldEditText> mEditFields;

    private boolean mModified;


    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerExternalStorageListener();

        setContentView(R.layout.card_editor);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);

        mSave = (Button) findViewById(R.id.CardEditorSaveButton);
        mCancel = (Button) findViewById(R.id.CardEditorCancelButton);

        if (getIntent().getBooleanExtra("callfromcardbrowser", false)) {
            mEditorCard = CardBrowser.getEditorCard();
        } else {
            mEditorCard = Reviewer.getEditorCard();
        }

        // Card -> FactID -> FieldIDs -> FieldModels

        Fact cardFact = mEditorCard.getFact();
        TreeSet<Field> fields = cardFact.getFields();

        mEditFields = new LinkedList<FieldEditText>();

        mModified = false;

        // Generate a new EditText for each field
        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            FieldEditText newTextbox = new FieldEditText(this, iter.next());
            TextView label = newTextbox.getLabel();
            mEditFields.add(newTextbox);

            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(newTextbox);
        }

        mSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
            	Iterator<FieldEditText> iter = mEditFields.iterator();
                while (iter.hasNext()) {
                    FieldEditText current = iter.next();
                    mModified |= current.updateField();
                }
                // Only send result to save if something was actually changed
                if (mModified) {
                    setResult(RESULT_OK);
                } else {
                    setResult(RESULT_CANCELED);
                }
                closeCardEditor();
            }

        });

        mCancel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                closeCardEditor();
            }

        });
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardEditor - onBackPressed()");
            closeCardEditor();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        finishNoStorageAvailable();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private void finishNoStorageAvailable() {
        setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
        finish();
    }


    private void closeCardEditor() {
        finish();
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
            MyAnimation.slide(CardEditor.this, MyAnimation.RIGHT);
        }    
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    private class FieldEditText extends EditText {

        private Field mPairField;


        public FieldEditText(Context context, Field pairField) {
            super(context);
            mPairField = pairField;
            this.setText(pairField.getValue());
        }


        public TextView getLabel() {
            TextView label = new TextView(this.getContext());
            label.setText(mPairField.getFieldModel().getName());
            return label;
        }


        public boolean updateField() {
            String newValue = this.getText().toString();
            if (!mPairField.getValue().equals(newValue)) {
                mPairField.setValue(newValue);
                return true;
            }
            return false;
        }
    }

}
