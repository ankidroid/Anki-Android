package com.ichi2.anki.multimediacard.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView.ExecEscaped;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.Toolbar;
import timber.log.Timber;

//BUG: Initial undo will undo the initial text
public class VisualEditorActivity extends AnkiActivity {

    public static final String EXTRA_FIELD = "visual.card.ed.extra.current.field";
    public static final String EXTRA_FIELD_INDEX = "visual.card.ed.extra.current.field.index";

    /** The Id of the current model (long) */
    public static final String EXTRA_MODEL_ID = "visual.card.ed.extra.model.id";
    /** All fields in a (string[])  */
    public static final String EXTRA_ALL_FIELDS = "visual.card.ed.extra.all.fields";


    private String mCurrentText;
    private IField mField;
    private int mIndex;
    private VisualEditorWebView mWebView;
    private int clozeId;
    private String[] allFields;
    private long mModelId;
    private String[] mFields;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.visual_editor);

        if (!setFieldsOnStartup()) {
            failStartingVisualEditor();
            return;
        }

        setupWebView(mWebView);

        setupEditorScrollbarButtons();

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        //TODO: I think we can move this up
        startLoadingCollection();
    }


    private void setupEditorScrollbarButtons() {
        SimpleListenerSetup setJsAction = (id, f) -> findViewById(id).setOnClickListener(v -> mWebView.execFunction(f));

        setJsAction.apply(R.id.editor_button_bold, "setBold");
        setJsAction.apply(R.id.editor_button_italic, "setItalic");
        setJsAction.apply(R.id.editor_button_underline, "setUnderline");

        findViewById(R.id.editor_button_cloze).setOnClickListener(v -> cloze(clozeId++));
    }


    private void setupWebView(VisualEditorWebView webView) {
        //TODO: Duplication
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                AnkiDroidApp.getSharedPrefs(this).getBoolean("html_javascript_debugging", false)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }


        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        CardAppearance cardAppearance = CardAppearance.create(new ReviewerCustomFonts(this), preferences);
        String css = cardAppearance.getStyle();
        mWebView.injectCss(css);
        webView.injectCss(css);
        webView.setOnTextChangeListener(s -> this.mCurrentText = s);

        webView.setHtml(mField.getText());
        //reset the note history so we can't undo the above action.
        webView.execFunction("clearHistory");

        //Could be better, this is done per card in AbstractFlashCardViewer
        webView.getSettings().setDefaultFontSize(CardAppearance.calculateDynamicFontSize(mField.getText()));
    }


    private boolean setFieldsOnStartup() {
        Bundle extras = this.getIntent().getExtras();
        if (extras == null) {
            Timber.w("No Extras in Bundle");
            return false;
        }

        //TODO: Save past data for later so we can see if we've changed.
        mField = (IField) extras.getSerializable(VisualEditorActivity.EXTRA_FIELD);
        Integer index = (Integer) extras.getSerializable(VisualEditorActivity.EXTRA_FIELD_INDEX);

        this.mFields = (String[]) extras.getSerializable(VisualEditorActivity.EXTRA_ALL_FIELDS);
        Long modelId = (Long) extras.getSerializable(VisualEditorActivity.EXTRA_MODEL_ID);

        if (mField == null) {
            Timber.w("Failed to find mField");
            return false;
        }
        if (index == null) {
            Timber.w("Failed to find index");
            return false;
        }
        if (mFields == null) {
            return false;
        }
        if (modelId == null) {
            return false;
        }

        this.mModelId = modelId;

        mIndex = index;

        mWebView = this.findViewById(R.id.editor_view);

        if (mWebView == null) {
            Timber.w("Failed to find WebView");
            return false;
        }

        return true;
    }


    private void failStartingVisualEditor() {
        UIUtils.showThemedToast(this, "Unable to start visual editor", false);
        finishCancel();
    }


    private void cloze(int clozeId) {
        ExecEscaped e = ExecEscaped.fromString(String.format(Locale.US, "cloze(%d)", clozeId));
        mWebView.exec(e);
    }

    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        Timber.d("onCollectionLoaded");
        initWebView(col);

        String css = getModelCss(col);
        mWebView.injectCss(css);
    }


    private String getModelCss(Collection col) {
        try {
            String css = col.getModels().get(mModelId).getString("css");
            return css.replace(".card", ".note-editable ");
        } catch (Exception e) {
            UIUtils.showThemedToast(this, "Failed to load template CSS", false);
            return getDefaultCss();
        }
    }

    private String getDefaultCss() {
        return ".note-editable {\n"
              + " font-family: arial;\n"
              + " font-size: 20px;\n"
              + " text-align: center;\n"
              + " color: black;\n"
              + " background-color: white;\n }";
    }


    private void initWebView(Collection col) {
        String mBaseUrl = Utils.getBaseUrl(col.getMedia().dir());
        InputStream is;
        try {
            is = getAssets().open("visualeditor/visual_editor.html");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mWebView.init(is, mBaseUrl);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // NOTE: This is called every time a new question is shown via invalidate options menu
        getMenuInflater().inflate(R.menu.visual_editor, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_undo:
                Timber.i("Undo button pressed");
                mWebView.execFunction("undo");
                return true;
            case R.id.action_redo:
                Timber.i("Redo Undo button pressed");
                mWebView.execFunction("redo");
                return true;
            case R.id.action_save:
                Timber.i("Save button pressed");
                finishWithSuccess();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void finishWithSuccess() {
        this.mField.setText(mCurrentText);
        Intent resultData = new Intent();
        resultData.putExtra(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD, this.mField);
        resultData.putExtra(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD_INDEX, this.mIndex);
        setResult(RESULT_OK, resultData);
        finishActivityWithFade(this);
    }

    private void finishCancel() {
        setResult(RESULT_CANCELED);
        finishActivityWithFade(this);
    }


    @FunctionalInterface
    protected interface SimpleListenerSetup {
        void apply(@IdRes int buttonId, String function);
    }
}
