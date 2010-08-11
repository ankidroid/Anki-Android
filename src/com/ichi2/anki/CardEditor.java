package com.ichi2.anki;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.Fact.Field;

/**
 * Allows the user to edit a fact, for instance if there is a typo.
 * 
 * A card is a presentation of a fact, and has two sides: a question and an answer.
 * Any number of fields can appear on each side.
 * When you add a fact to Anki, cards which show that fact are generated.
 * Some models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class CardEditor extends Activity {

    private LinearLayout fieldsLayoutContainer;
    
    private Button mSave;
    private Button mCancel;
    
    private Card editorCard;
    
    LinkedList<FieldEditText> editFields;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_editor);
        
        fieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);
        
        mSave = (Button) findViewById(R.id.CardEditorSaveButton);
        mCancel = (Button) findViewById(R.id.CardEditorCancelButton);

        editorCard = AnkiDroid.getEditorCard();

        // Card -> FactID -> FieldIDs -> FieldModels
        
        Fact cardFact = editorCard.getFact();
        TreeSet<Field> fields = cardFact.getFields();
        
        editFields = new LinkedList<FieldEditText>();
        
        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            FieldEditText newTextbox = new FieldEditText(this, iter.next());
            TextView label = newTextbox.getLabel();
            editFields.add(newTextbox);
            
            fieldsLayoutContainer.addView(label);
            fieldsLayoutContainer.addView(newTextbox);
            // Generate a new EditText for each field
            
        }

        mSave.setOnClickListener(new View.OnClickListener() 
        {

            public void onClick(View v) {
                
                Iterator<FieldEditText> iter = editFields.iterator();
                while (iter.hasNext())
                {
                    FieldEditText current = iter.next();
                    current.updateField();
                }
                setResult(RESULT_OK);
                finish();
            }
            
        });
        
        mCancel.setOnClickListener(new View.OnClickListener() 
        {
            
            public void onClick(View v) {
                
                setResult(RESULT_CANCELED);
                finish();
                
            }
            
        });
    }

    private class FieldEditText extends EditText
    {

        Field pairField;
        
        public FieldEditText(Context context, Field pairField) {
            super(context);
            this.pairField = pairField;
            this.setText(pairField.value);
            // TODO Auto-generated constructor stub
        }
        
        public TextView getLabel() 
        {
            TextView label = new TextView(this.getContext());
            label.setText(pairField.fieldModel.name);
            return label;
        }
        
        public void updateField()
        {
            pairField.value = this.getText().toString();
        }
    }
}
