package com.ichi2.anki;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class CardEditor  extends Activity {

    public static final int SAVE_CARD = 0;
    public static final int CANCEL = 1;
    
    private EditText mQuestionText;
    private EditText mAnswerText;
    
    private Button mSave;
    private Button mCancel;
    
    private Card editorCard;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        setContentView(R.layout.card_editor);
        
        mQuestionText = (EditText) findViewById(R.id.CardEditorQuestionEditText);
        mAnswerText = (EditText) findViewById(R.id.CardEditorAnswerEditText);
        
        mSave = (Button) findViewById(R.id.CardEditorSaveButton);
        mCancel = (Button) findViewById(R.id.CardEditorCancelButton);

        editorCard = Ankidroid.getEditorCard();

        String oldQuestion = editorCard.question;
        String oldAnswer = editorCard.answer;

        if (oldQuestion != null)
        {
            mQuestionText.setText(oldQuestion);
        }

        if (oldAnswer != null) 
        {
            mAnswerText.setText(oldAnswer);
        } 


        
        mSave.setOnClickListener(new View.OnClickListener() 
        {

            public void onClick(View v) {
                
                editorCard.question = mQuestionText.getText().toString();
                editorCard.answer = mAnswerText.getText().toString();
                
                setResult(SAVE_CARD);
                finish();
            }
            
        });
        
        mCancel.setOnClickListener(new View.OnClickListener() 
        {
            
            public void onClick(View v) {
                
                setResult(CANCEL);
                finish();
                
            }
            
        });
    }

}
