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
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView.ExecEscaped;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;

import java.util.Locale;

import androidx.annotation.IdRes;
import androidx.appcompat.widget.Toolbar;
import timber.log.Timber;

public class VisualEditorActivity extends AnkiActivity {

    public static final String EXTRA_FIELD = "visual.card.ed.extra.current.field";
    public static final String EXTRA_FIELD_INDEX = "visual.card.ed.extra.current.field.index";

    private String mCurrentText;
    private IField mField;
    private int mIndex;
    private VisualEditorWebView mWebView;
    private String mBaseUrl;
    private int cloze;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.visual_editor);
        //TODO: Save data for later so we can see if we;ve changed.
        mField = (IField) this.getIntent().getExtras().getSerializable(VisualEditorActivity.EXTRA_FIELD);
        mIndex = (int) this.getIntent().getExtras().getSerializable(VisualEditorActivity.EXTRA_FIELD_INDEX);

        mWebView = this.findViewById(R.id.editor_view);

        //TODO: Duplication
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                AnkiDroidApp.getSharedPrefs(this).getBoolean("html_javascript_debugging", false)) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        //TODO: actually get the template CSS
        String defaultCardCss = ".note-editable {\n"
                + " font-family: arial;\n"
                + " font-size: 20px;\n"
                + " text-align: center;\n"
                + " color: black;\n"
                + " background-color: white;\n }";

        //TODO: fix hardcoded zoom constants.
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        CardAppearance cardAppearance = CardAppearance.create(new ReviewerCustomFonts(this), preferences);
        String css = cardAppearance.getStyle();
        mWebView.injectCss(defaultCardCss);
        mWebView.injectCss(css);
        mWebView.init();
        mWebView.setOnTextChangeListener(s -> this.mCurrentText = s);

        mWebView.setHtml(mField.getText());

        //Could be better, this is done per card in AbstractFlashCardViewer
        mWebView.getSettings().setDefaultFontSize(CardAppearance.calculateDynamicFontSize(mField.getText()));

        SimpleListenerSetup setJsAction = (id, f) -> findViewById(id).setOnClickListener(v -> mWebView.execNiladicFunction(f));

        setJsAction.apply(R.id.editor_button_bold, "setBold");
        setJsAction.apply(R.id.editor_button_italic, "setItalic");
        setJsAction.apply(R.id.editor_button_underline, "setUnderline");

        findViewById(R.id.editor_button_cloze).setOnClickListener(v -> cloze(cloze++));

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    private void cloze(int clozeId) {
        ExecEscaped e = ExecEscaped.fromString(String.format(Locale.US, "cloze(%d)", clozeId));
        mWebView.exec(e);
    }

    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        mBaseUrl = Utils.getBaseUrl(col.getMedia().dir());
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
                mWebView.exec(ExecEscaped.fromString("undo"));
                return true;
            case R.id.action_redo:
                Timber.i("Redo Undo button pressed");
                mWebView.exec(ExecEscaped.fromString("redo"));
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


    @FunctionalInterface
    protected interface SimpleListenerSetup {
        void apply(@IdRes int buttonId, String function);
    }
}
