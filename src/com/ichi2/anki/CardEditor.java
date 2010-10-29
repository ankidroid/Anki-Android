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
 * Allows the user to edit a fact, for instance if there is a typo. A card is a presentation of a fact, and has two
 * sides: a question and an answer. Any number of fields can appear on each side. When you add a fact to Anki, cards
 * which show that fact are generated. Some models generate one card, others generate more than one.
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

        mEditorCard = Reviewer.getEditorCard();

        // Card -> FactID -> FieldIDs -> FieldModels

        Fact cardFact = mEditorCard.getFact();
        TreeSet<Field> fields = cardFact.getFields();

        mEditFields = new LinkedList<FieldEditText>();

        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            FieldEditText newTextbox = new FieldEditText(this, iter.next());
            TextView label = newTextbox.getLabel();
            mEditFields.add(newTextbox);

            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(newTextbox);
            // Generate a new EditText for each field

        }

        mSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Iterator<FieldEditText> iter = mEditFields.iterator();
                while (iter.hasNext()) {
                    FieldEditText current = iter.next();
                    current.updateField();
                }
                setResult(RESULT_OK);
                finish();
            }

        });

        mCancel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }

        });
    }


    @Override
    public void onDestroy() {
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

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    private class FieldEditText extends EditText {

        private Field mPairField;


        public FieldEditText(Context context, Field pairField) {
            super(context);
            mPairField = pairField;
            this.setText(pairField.getValue());
            // TODO Auto-generated constructor stub
        }


        public TextView getLabel() {
            TextView label = new TextView(this.getContext());
            label.setText(mPairField.getFieldModel().getName());
            return label;
        }


        public void updateField() {
            mPairField.setValue(this.getText().toString());
        }
    }

}
