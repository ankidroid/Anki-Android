package com.ichi2.anki;

import java.util.ArrayList;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.ichi2.anki.glosbe.json.Response;
import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;

/**
 * @author zaur
 * 
 */
public class BasicTextFieldController implements IFieldController, DialogInterface.OnClickListener
{

    private static final int REQUEST_CODE_TRANSLATION = 101;
    private static final int REQUEST_CODE_PRONOUNCIATION = 102;
    IField mField;
    IMultimediaEditableNote mNote;
    private int mIndex;
    private EditText mEditText;
    private FragmentActivity mActivity;
    private ArrayList<String> mPossibleClones;
    private String mjson;

    @Override
    public void setField(IField field)
    {
        mField = field;
    }

    @Override
    public void setNote(IMultimediaEditableNote note)
    {
        mNote = note;
    }

    @Override
    public void setFieldIndex(int index)
    {
        mIndex = index;
    }

    @Override
    public void setFragmentActivity(FragmentActivity activity)
    {
        mActivity = activity;
    };

    @Override
    public void createUI(LinearLayout layout)
    {
        mEditText = new EditText(mActivity);
        mEditText.setMinLines(3);
        mEditText.setText(mField.getText());
        layout.addView(mEditText, LinearLayout.LayoutParams.MATCH_PARENT);

        LinearLayout layoutTools = new LinearLayout(mActivity);
        layoutTools.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(layoutTools);

        createCloneButton(layoutTools);
        createTranslateButton(layoutTools);
        createPronounceButton(layoutTools);
        
//        createTmpButton(layoutTools);

    }

    
    //Test of JSON parsing
//    private void createTmpButton(LinearLayout layoutTools)
//    {
//        mjson = "{\n" +
//                "  \"result\" : \"ok\",\n" +
//                "  \"dest\" : \"eng\",\n" +
//                "  \"phrase\" : \"witaj\",\n" +
//                "  \"tuc\" : [ {\n" +
//                "    \"authors\" : [ 1 ],\n" +
//                "    \"meaningId\" : 30425,\n" +
//                "    \"meanings\" : [ {\n" +
//                "      \"text\" : \"greeting\",\n" +
//                "      \"language\" : \"eng\"\n" +
//                "    } ],\n" +
//                "    \"phrase\" : {\n" +
//                "      \"text\" : \"hello\",\n" +
//                "      \"languageCode\" : \"eng\"\n" +
//                "    }\n" +
//                "  }, {\n" +
//                "    \"authors\" : [ 35 ],\n" +
//                "    \"meaningId\" : 6081957,\n" +
//                "    \"meanings\" : [ {\n" +
//                "      \"text\" : \"A greeting used when someone arrives.\",\n" +
//                "      \"language\" : \"eng\"\n" +
//                "    } ],\n" +
//                "    \"phrase\" : {\n" +
//                "      \"text\" : \"welcome\",\n" +
//                "      \"languageCode\" : \"eng\"\n" +
//                "    }\n" +
//                "  }, {\n" +
//                "    \"authors\" : [ 1 ],\n" +
//                "    \"meaningId\" : 13025,\n" +
//                "    \"meanings\" : [ {\n" +
//                "      \"text\" : \"greeting given upon someone&#39;s arrival\",\n" +
//                "      \"language\" : \"eng\"\n" +
//                "    } ],\n" +
//                "    \"phrase\" : {\n" +
//                "      \"text\" : \"welcome\",\n" +
//                "      \"languageCode\" : \"eng\"\n" +
//                "    }\n" +
//                "  }, {\n" +
//                "    \"authors\" : [ 1 ],\n" +
//                "    \"meaningId\" : 742087,\n" +
//                "    \"meanings\" : [ {\n" +
//                "      \"text\" : \"welcome\",\n" +
//                "      \"language\" : \"eng\"\n" +
//                "    } ],\n" +
//                "    \"phrase\" : null\n" +
//                "  }, {\n" +
//                "    \"authors\" : [ 1 ],\n" +
//                "    \"meaningId\" : 742083,\n" +
//                "    \"meanings\" : [ {\n" +
//                "      \"text\" : \"hello\",\n" +
//                "      \"language\" : \"eng\"\n" +
//                "    } ],\n" +
//                "    \"phrase\" : null\n" +
//                "  } ],\n" +
//                "  \"from\" : \"pol\"\n" +
//                "}";
//        
//        Button tmpButton = new Button(mActivity);
//        tmpButton.setText("Json");
//        tmpButton.setOnClickListener(new OnClickListener()
//        {
//            
//            @Override
//            public void onClick(View v)
//            {
//                translateJson();                
//            }
//        }); 
//        
//        layoutTools.addView(tmpButton);
//    }

    private void createPronounceButton(LinearLayout layoutTools)
    {
        Button btnPronounce = new Button(mActivity);
        // TODO Translation
        btnPronounce.setText("Pronounce");
        btnPronounce.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String source =mEditText.getText().toString(); 
                
                if (source.length() == 0)
                {
                    // TODO Translation
                    showToast("Input some text first...");
                    return;
                }
                
                Intent intent = new Intent(mActivity, LoadPronounciationActivity.class);
                intent.putExtra(LoadPronounciationActivity.EXTRA_SOURCE, source);
                mActivity.startActivityForResult(intent, REQUEST_CODE_TRANSLATION);
            }
        });
        
        layoutTools.addView(btnPronounce);
    }

    // Here is all the functionality to provide translations
    private void createTranslateButton(LinearLayout layoutTools)
    {
        Button btnTranslate = new Button(mActivity);
        // TODO Translation
        btnTranslate.setText("Translate");
        btnTranslate.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String source =mEditText.getText().toString(); 
                
                if (source.length() == 0)
                {
                    // TODO Translation
                    showToast("Input some text first...");
                    return;
                }
                
                Intent intent = new Intent(mActivity, TranslationActivity.class);
                intent.putExtra(TranslationActivity.EXTRA_SOURCE, source);
                mActivity.startActivityForResult(intent, REQUEST_CODE_TRANSLATION);
            }
        });
        
        layoutTools.addView(btnTranslate);
    }

    /**
     * @param activity
     * @param layoutTools
     * 
     *            This creates a button, which will call a dialog, allowing to
     *            pick from another note's fields one, and use it's value in the
     *            current one.
     * 
     */
    private void createCloneButton(LinearLayout layoutTools)
    {
        if (mNote.getNumberOfFields() > 1)
        {
            // Should be more than one text not empty fields for clone to make
            // sense

            mPossibleClones = new ArrayList<String>();

            int numTextFields = 0;
            for (int i = 0; i < mNote.getNumberOfFields(); ++i)
            {
                IField curField = mNote.getField(i);
                if (curField == null)
                {
                    continue;
                }

                if (curField.getType() != EFieldType.TEXT)
                {
                    continue;
                }

                if (curField.getText().length() == 0)
                {
                    continue;
                }

                if (curField.getText().contentEquals(mField.getText()))
                {
                    continue;
                }

                mPossibleClones.add(curField.getText());
                ++numTextFields;
            }

            if (numTextFields < 1)
            {
                return;
            }

            Button btnOtherField = new Button(mActivity);
            // TODO Translation
            btnOtherField.setText("Clone");
            layoutTools.addView(btnOtherField);

            final BasicTextFieldController controller = this;

            btnOtherField.setOnClickListener(new OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                    PickStringDialogFragment fragment = new PickStringDialogFragment();

                    fragment.setChoices(mPossibleClones);
                    fragment.setOnclickListener(controller);
                    //TODO translate
                    fragment.setTitle("Pick");

                    fragment.show(mActivity.getSupportFragmentManager(), "pick.clone");
                }
            });

        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if(requestCode == REQUEST_CODE_TRANSLATION && resultCode == Activity.RESULT_OK)
        {
            //Translation returned.
            try
            {
                String translation = data.getExtras().get(TranslationActivity.EXTRA_TRANSLATION).toString();
                mEditText.setText(translation);
            }
            catch(Exception e)
            {
                //TODO Translation
                showToast("Translation failed");
            }
        }
    }

    @Override
    public void onDone()
    {
        mField.setText(mEditText.getText().toString());
    }

    // This is when the dialog for clone ends
    @Override
    public void onClick(DialogInterface dialog, int which)
    {
        mEditText.setText(mPossibleClones.get(which));
    }

    /**
     * @param text
     * 
     *            A short cut to show a toast
     */
    private void showToast(CharSequence text)
    {
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(mActivity, text, duration);
        toast.show();
    }

}
