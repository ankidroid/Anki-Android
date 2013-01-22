package com.ichi2.anki;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import com.ichi2.anki.multimediacard.EFieldType;
import com.ichi2.anki.multimediacard.IField;
import com.ichi2.anki.multimediacard.impl.AudioField;
import com.ichi2.anki.multimediacard.impl.ImageField;

/**
 * @author zaur
 * 
 */
public class BasicTextFieldController extends FieldControllerBase implements IFieldController,
        DialogInterface.OnClickListener
{

    private static final int REQUEST_CODE_TRANSLATE_GLOSBE = 101;
    private static final int REQUEST_CODE_PRONOUNCIATION = 102;
    private static final int REQUEST_CODE_TRANSLATE_COLORDICT = 103;
    private static final int REQUEST_CODE_IMAGE_SEARCH = 104;

    private EditText mEditText;
    private ArrayList<String> mPossibleClones;
    private String mjson;

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

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);

        createCloneButton(layoutTools, p);
        createClearButton(layoutTools, p);
        
        
        
        LinearLayout layoutTools2 = new LinearLayout(mActivity);
        layoutTools2.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(layoutTools2);

        createTranslateButton(layoutTools2, p);
        createPronounceButton(layoutTools2, p);
        createSearchImageButton(layoutTools2, p);

    }

    private void createSearchImageButton(LinearLayout layoutTools, LayoutParams p)
    {
        Button clearButton = new Button(mActivity);
        clearButton.setText("Image");
        layoutTools.addView(clearButton, p);

        clearButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                String source = mEditText.getText().toString();

                if (source.length() == 0)
                {
                    // TODO Translation
                    showToast("Input some text first...");
                    return;
                }

                Intent intent = new Intent(mActivity, SearchImageActivity.class);
                intent.putExtra(SearchImageActivity.EXTRA_SOURCE, source);
                mActivity.startActivityForResult(intent, REQUEST_CODE_IMAGE_SEARCH);
            }
        });
    }

    private void createClearButton(LinearLayout layoutTools, LayoutParams p)
    {
        Button clearButton = new Button(mActivity);
        clearButton.setText("Clear");
        layoutTools.addView(clearButton, p);

        clearButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                mEditText.setText("");

            }
        });
    }

    private void createPronounceButton(LinearLayout layoutTools, LayoutParams p)
    {
        Button btnPronounce = new Button(mActivity);
        // TODO Translation
        btnPronounce.setText("Pronounce");
        btnPronounce.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String source = mEditText.getText().toString();

                if (source.length() == 0)
                {
                    // TODO Translation
                    showToast("Input some text first...");
                    return;
                }

                Intent intent = new Intent(mActivity, LoadPronounciationActivity.class);
                intent.putExtra(LoadPronounciationActivity.EXTRA_SOURCE, source);
                mActivity.startActivityForResult(intent, REQUEST_CODE_PRONOUNCIATION);
            }
        });

        layoutTools.addView(btnPronounce, p);
    }

    // Here is all the functionality to provide translations
    private void createTranslateButton(LinearLayout layoutTool, LayoutParams ps)
    {
        Button btnTranslate = new Button(mActivity);
        // TODO Translation
        btnTranslate.setText("Translate");
        btnTranslate.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                PickStringDialogFragment fragment = new PickStringDialogFragment();

                ArrayList<String> translationSources = new ArrayList<String>();
                translationSources.add("Glosbe.com");
                translationSources.add("ColorDict");

                fragment.setChoices(translationSources);
                fragment.setOnclickListener(new DialogInterface.OnClickListener()
                {

                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        if (which == 0)
                        {
                            startTranslationWithGlosbe();
                        }
                        else if (which == 1)
                        {
                            startTranslationWithColorDict();
                        }
                    }
                });

                // TODO translate
                fragment.setTitle("Pick");

                fragment.show(mActivity.getSupportFragmentManager(), "pick.translation.source");
            }
        });

        layoutTool.addView(btnTranslate, ps);
    }

    /**
     * @param activity
     * @param layoutTools
     * 
     *            This creates a button, which will call a dialog, allowing to
     *            pick from another note's fields one, and use it's value in the
     *            current one.
     * @param p 
     * 
     */
    private void createCloneButton(LinearLayout layoutTools, LayoutParams p)
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

                if (curField.getText() == null)
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
            layoutTools.addView(btnOtherField, p);

            final BasicTextFieldController controller = this;

            btnOtherField.setOnClickListener(new OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                    PickStringDialogFragment fragment = new PickStringDialogFragment();

                    fragment.setChoices(mPossibleClones);
                    fragment.setOnclickListener(controller);
                    // TODO translate
                    fragment.setTitle("Pick");

                    fragment.show(mActivity.getSupportFragmentManager(), "pick.clone");
                }
            });

        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE_TRANSLATE_GLOSBE && resultCode == Activity.RESULT_OK)
        {
            // Translation returned.
            try
            {
                String translation = data.getExtras().get(TranslationActivity.EXTRA_TRANSLATION).toString();
                mEditText.setText(translation);
            }
            catch (Exception e)
            {
                // TODO Translation
                showToast("Translation failed");
            }
        }
        else if (requestCode == REQUEST_CODE_PRONOUNCIATION && resultCode == Activity.RESULT_OK)
        {
            try
            {
                String pronuncPath = data.getExtras().get(LoadPronounciationActivity.EXTRA_PRONUNCIATION_FILE_PATH)
                        .toString();
                File f = new File(pronuncPath);
                if (!f.exists())
                {
                    // TODO Translation
                    showToast("Getting pronunciation failed");
                }

                AudioField af = new AudioField();
                af.setAudioPath(pronuncPath);
                mActivity.handleFieldChanged(af);
            }
            catch (Exception e)
            {
                // TODO Translation
                showToast("Getting pronunciation failed");
            }
        }
        else if (requestCode == REQUEST_CODE_TRANSLATE_COLORDICT && resultCode == Activity.RESULT_OK)
        {
            String subject = data.getStringExtra(Intent.EXTRA_SUBJECT);
            String text = data.getStringExtra(Intent.EXTRA_TEXT);

            mEditText.setText(subject + "\n" + text);

        }
        else if (requestCode == REQUEST_CODE_IMAGE_SEARCH && resultCode == Activity.RESULT_OK)
        {
            String imgPath = data.getExtras().get(SearchImageActivity.EXTRA_IMAGE_FILE_PATH).toString();
            File f = new File(imgPath);
            if (!f.exists())
            {
                // TODO Translation
                showToast("Getting image failed");
            }

            ImageField imgField = new ImageField();
            imgField.setImagePath(imgPath);
            mActivity.handleFieldChanged(imgField);
        }
    }

    public static boolean isIntentAvailable(Context context, Intent intent)
    {
        final PackageManager packageManager = context.getPackageManager();
        List<?> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
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

    // Only now not all APIs are used, may be later, they will be.
    @SuppressWarnings("unused")
    protected void startTranslationWithColorDict()
    {
        final String PICK_RESULT_ACTION = "colordict.intent.action.PICK_RESULT";
        final String SEARCH_ACTION = "colordict.intent.action.SEARCH";
        final String EXTRA_QUERY = "EXTRA_QUERY";
        final String EXTRA_FULLSCREEN = "EXTRA_FULLSCREEN";
        final String EXTRA_HEIGHT = "EXTRA_HEIGHT";
        final String EXTRA_WIDTH = "EXTRA_WIDTH";
        final String EXTRA_GRAVITY = "EXTRA_GRAVITY";
        final String EXTRA_MARGIN_LEFT = "EXTRA_MARGIN_LEFT";
        final String EXTRA_MARGIN_TOP = "EXTRA_MARGIN_TOP";
        final String EXTRA_MARGIN_BOTTOM = "EXTRA_MARGIN_BOTTOM";
        final String EXTRA_MARGIN_RIGHT = "EXTRA_MARGIN_RIGHT";

        Intent intent = new Intent(PICK_RESULT_ACTION);
        intent.putExtra(EXTRA_QUERY, mEditText.getText().toString()); // Search
                                                                      // Query
        intent.putExtra(EXTRA_FULLSCREEN, false); //
        // intent.putExtra(EXTRA_HEIGHT, 400); //400pixel, if you don't specify,
        // fill_parent"
        intent.putExtra(EXTRA_GRAVITY, Gravity.BOTTOM);
        // intent.putExtra(EXTRA_MARGIN_LEFT, 100);
        if (!isIntentAvailable(mActivity, intent))
        {
            showToast("Install ColorDict first");
            return;
        }
        mActivity.startActivityForResult(intent, REQUEST_CODE_TRANSLATE_COLORDICT);
    }

    protected void startTranslationWithGlosbe()
    {
        String source = mEditText.getText().toString();

        if (source.length() == 0)
        {
            // TODO Translation
            showToast("Input some text first...");
            return;
        }

        Intent intent = new Intent(mActivity, TranslationActivity.class);
        intent.putExtra(TranslationActivity.EXTRA_SOURCE, source);
        mActivity.startActivityForResult(intent, REQUEST_CODE_TRANSLATE_GLOSBE);
    }

}
