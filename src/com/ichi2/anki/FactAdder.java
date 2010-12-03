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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.res.Resources;

import com.ichi2.anki.Fact.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * Allows the user to add a fact. A card is a presentation of a fact, and has two sides: a question and an answer. Any
 * number of fields can appear on each side. When you add a fact to Anki, cards which show that fact are generated. Some
 * models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class FactAdder extends Activity {

    private static final int DIALOG_MODEL_SELECT = 0;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    private LinearLayout mFieldsLayoutContainer;
    private HashMap<Long, Model> mModels;

    private Button mAddButton;
    private Button mCloseButton;
    private Button mModelButton;
    private TextView mCardTemplates;

    private Deck mDeck;
    private Long mCurrentSelectedModelId;

    private LinkedList<FieldEditText> mEditFields;

    private Fact mNewFact;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Resources res = getResources();
        
        registerExternalStorageListener();

        setContentView(R.layout.fact_adder);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.FactAdderEditFieldsLayout);

        mAddButton = (Button) findViewById(R.id.FactAdderAddButton);
        mCloseButton = (Button) findViewById(R.id.FactAdderCloseButton);
        mModelButton = (Button) findViewById(R.id.FactAdderModelButton);
        mCardTemplates = (TextView) findViewById(R.id.FactAdderTemplates);
        
        mDeck = AnkiDroidApp.deck();

        mModels = Model.getModels(mDeck);
        mCurrentSelectedModelId = mDeck.getCurrentModelId();
        mModelButton.setText(mModels.get(mCurrentSelectedModelId).getName());
        //res.getString(R.string.card)
        mCardTemplates.setText(res.getString(R.string.card) + " " + mModels.get(mCurrentSelectedModelId).getCardModelNames());
        
        mNewFact = mDeck.newFact();
        populateEditFields();
        mAddButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                for (FieldEditText current : mEditFields) {
                    current.updateField();
                }
                mDeck.addFact(mNewFact);
                setResult(RESULT_OK);
                finish();
            }

        });

        mModelButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                showDialog(DIALOG_MODEL_SELECT);

            }

        });

        mCloseButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }

        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;

        switch (id) {
            case DIALOG_MODEL_SELECT:
                ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
                // Use this array to know which ID is associated with each Item(name)
                final ArrayList<Long> dialogIds = new ArrayList<Long>();

                Model mModel;

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.select_model);
                for (Long i : mModels.keySet()) {
                    mModel = mModels.get(i);
                    dialogItems.add(mModel.getName());
                    dialogIds.add(i);
                }
                // Convert to Array
                CharSequence[] items = new CharSequence[dialogItems.size()];
                dialogItems.toArray(items);

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        long oldModelId = mCurrentSelectedModelId;
                        mCurrentSelectedModelId = dialogIds.get(item);
                        mModelButton.setText(mModels.get(mCurrentSelectedModelId).getName());

                        if (oldModelId != mCurrentSelectedModelId) {
                            populateEditFields();
                        }
                    }
                });
                AlertDialog alert = builder.create();
                return alert;
            default:
                dialog = null;
        }
        return dialog;
    }


    private void populateEditFields() {
        mFieldsLayoutContainer.removeAllViews();
        mEditFields = new LinkedList<FieldEditText>();
        TreeSet<Field> fields = mNewFact.getFields();
        for (Field f : fields) {
            FieldEditText newTextbox = new FieldEditText(this, f);
            TextView label = newTextbox.getLabel();
            mEditFields.add(newTextbox);

            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(newTextbox);
            // Generate a new EditText for each field
        }
    }


    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     */
    private void registerExternalStorageListener() {
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

    // TODO: remove redundance with CardEditor.java::FieldEditText
    private class FieldEditText extends EditText {

        private Field mPairField;


        public FieldEditText(Context context, Field pairField) {
            super(context);
            mPairField = pairField;
            this.setMinimumWidth(400);
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
