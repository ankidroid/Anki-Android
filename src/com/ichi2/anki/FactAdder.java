package com.ichi2.anki;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ichi2.anki.Model;
/**
 * Allows the user to add a fact.
 * 
 * A card is a presentation of a fact, and has two sides: a question and an answer.
 * Any number of fields can appear on each side.
 * When you add a fact to Anki, cards which show that fact are generated.
 * Some models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */

public class FactAdder extends Activity {

    /**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;
	
    private LinearLayout mFieldsLayoutContainer;
    HashMap<Long, Model> models;
    private LinkedList<FieldEditText> mEditFields;
    Deck deck;
    private Button addButton, closeButton, modelButton;
    static final int DIALOG_MODEL_SELECT = 0;

    private Long currentSelectedModelId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerExternalStorageListener();
        
        setContentView(R.layout.fact_adder);
        
        mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.FactAdderEditFieldsLayout);
        
        addButton = (Button) findViewById(R.id.FactAdderAddButton);
        closeButton = (Button) findViewById(R.id.FactAdderCloseButton);
        modelButton = (Button) findViewById(R.id.FactAdderModelButton);
        deck = AnkiDroidApp.deck();
        
        
        models=Model.getModels(deck);
        currentSelectedModelId=deck.currentModelId;
        modelButton.setText(models.get(currentSelectedModelId).name);
        populateEditFields();
        addButton.setOnClickListener(new View.OnClickListener() 
        {

            public void onClick(View v) {
            	Fact f = deck.newFact();
            	Log.i("Debug",Long.toString(f.id));
                setResult(RESULT_OK);
                finish();
            }
            
        });

        modelButton.setOnClickListener(new View.OnClickListener() 
        {

            public void onClick(View v) {
            	
                showDialog(DIALOG_MODEL_SELECT);
            }
            
        });

        
        closeButton.setOnClickListener(new View.OnClickListener() 
        {
            
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
            
        });
    }

    @Override
    public void onDestroy()
    {
    	super.onDestroy();
    	if(mUnmountReceiver != null)
    		unregisterReceiver(mUnmountReceiver);
    }
	
    private void populateEditFields(){
    	FieldModel mFieldModel;
        mEditFields = new LinkedList<FieldEditText>();
        TreeMap<Long,FieldModel> fields = models.get(currentSelectedModelId).getFieldModels();
        for (Long i:fields.keySet()){
    		mFieldModel=fields.get(i);
            FieldEditText newTextbox = new FieldEditText(this, mFieldModel);
            TextView label = newTextbox.getLabel();
            mEditFields.add(newTextbox);
            mFieldsLayoutContainer.addView(label);
            mFieldsLayoutContainer.addView(newTextbox);
    	}

    }
    
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        
        switch(id) {
        case DIALOG_MODEL_SELECT:
        	ArrayList<CharSequence> dialogItems=new ArrayList<CharSequence>();
        	// Use this array to know which ID is associated with each Item(name)
        	final ArrayList<Long> dialogIds=new ArrayList<Long>();
        	
        	Model mModel;

        	
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setTitle("Select Model:");
        	for (Long i:models.keySet()){
        		mModel=models.get(i);
        		dialogItems.add(mModel.name);
        		dialogIds.add(i);
        	}
        	// Convert to Array
        	CharSequence items[] = new CharSequence[dialogItems.size()];
        	dialogItems.toArray(items);
        	
        	builder.setItems(items, new DialogInterface.OnClickListener() {
        	    public void onClick(DialogInterface dialog, int item) {
        	    	currentSelectedModelId=dialogIds.get(item);
        	        modelButton.setText(models.get(currentSelectedModelId).name);
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

    private void finishNoStorageAvailable()
    {
    	setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
		finish();
    }

    private class FieldEditText extends EditText {

        FieldModel pairFieldModel;

        public FieldEditText(Context context, FieldModel pairFieldModel) {
            super(context);
            this.pairFieldModel = pairFieldModel;
            this.setMinimumWidth(400);
        }


        public TextView getLabel() {
            TextView label = new TextView(this.getContext());
            label.setText(pairFieldModel.name);
            return label;
        }

    }
}
