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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashMap;



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

    private Long mCurrentSelectedModelId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerExternalStorageListener();

        setContentView(R.layout.fact_adder);

        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.FactAdderEditFieldsLayout);

        mAddButton = (Button) findViewById(R.id.FactAdderAddButton);
        mCloseButton = (Button) findViewById(R.id.FactAdderCloseButton);
        mModelButton = (Button) findViewById(R.id.FactAdderModelButton);
        Deck deck = AnkiDroidApp.deck();

        mModels = Model.getModels(deck);
        mCurrentSelectedModelId = deck.getCurrentModelId();
        mModelButton.setText(mModels.get(mCurrentSelectedModelId).getName());
        mAddButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {

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
                builder.setTitle("Select Model:");
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
                        mCurrentSelectedModelId = dialogIds.get(item);
                        mModelButton.setText(mModels.get(mCurrentSelectedModelId).getName());
                        Log.i("Debug: id: ", dialogIds.get(item).toString());
                    }
                });
                AlertDialog alert = builder.create();
                return alert;
            default:
                dialog = null;
        }
        return dialog;
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

}
