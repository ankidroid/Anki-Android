package com.ichi2.anki.multimediacard.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.AudioRecordingField;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView.ExecEscaped;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView.SelectionType;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.AssetReader;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.WebViewDebugging;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import timber.log.Timber;

import static com.ichi2.anki.NoteEditor.REQUEST_MULTIMEDIA_EDIT;

//BUG: Initial undo will undo the initial text
//BUG: <hr/> is  less thick in the editor
//NOTE: Remove formatting on "{{c1::" will cause a failure to detect the cloze deletion, this is the same as Anki.
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
    private long mModelId;
    private String[] mFields;
    @NonNull
    private SelectionType mSelectionType = SelectionType.REGULAR;
    private AssetReader mAssetReader = new AssetReader(this);
    //Unsure if this is needed, or whether getCol will block until onCollectionLoaded completes.
    private boolean mHasLoadedCol;


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

        startLoadingCollection();
    }


    private void setupEditorScrollbarButtons() {
        SimpleListenerSetup setJsAction = (id, f) -> findViewById(id).setOnClickListener(v -> mWebView.execFunction(f));

        //defined: https://github.com/jkennethcarino/rtexteditorview/blob/master/library/src/main/assets/editor.js
        setJsAction.apply(R.id.editor_button_bold, "setBold");
        setJsAction.apply(R.id.editor_button_italic, "setItalic");
        setJsAction.apply(R.id.editor_button_underline, "setUnderline");
        setJsAction.apply(R.id.editor_button_clear_formatting, "removeFormat");
        setJsAction.apply(R.id.editor_button_list_bullet, "insertUnorderedList");
        setJsAction.apply(R.id.editor_button_list_numbered, "insertOrderedList");
        setJsAction.apply(R.id.editor_button_horizontal_rule, "insertHorizontalRule");
        setJsAction.apply(R.id.editor_button_align_left, "setAlignLeft");
        setJsAction.apply(R.id.editor_button_align_center, "setAlignCenter");
        setJsAction.apply(R.id.editor_button_align_right, "setAlignRight");
        setJsAction.apply(R.id.editor_button_align_justify, "setAlignJustify");
        setJsAction.apply(R.id.editor_button_view_html, "editHtml"); //this is a toggle.

        findViewById(R.id.editor_button_add_image).setOnClickListener(v -> this.openAdvancedViewerForAddImage());
        findViewById(R.id.editor_button_record_audio).setOnClickListener(v -> this.openAdvancedViewerForRecordAudio());


        findViewById(R.id.editor_button_cloze).setOnClickListener(v -> performCloze());
    }


    private void performCloze() {
        cloze(getNextClozeId());
    }


    private int getNextClozeId() {
        List<String> fields = Arrays.asList(mFields);
        fields.set(mIndex, mCurrentText);
        return Note.ClozeUtils.getNextClozeIndex(fields);
    }


    private void openAdvancedViewerForRecordAudio() {
        if (!checkCollectionHasLoaded(R.string.visual_editor_could_not_start_add_image)) {
            return;
        }
        IField field = new AudioRecordingField();
        openMultimediaEditor(field);
    }


    private void openAdvancedViewerForAddImage() {
        if (!checkCollectionHasLoaded(R.string.visual_editor_could_not_start_add_audio)) {
            return;
        }

        IField field = new ImageField();
        openMultimediaEditor(field);
    }


    private void openMultimediaEditor(IField field) {
        IMultimediaEditableNote mNote = NoteService.createEmptyNote(getCol().getModels().get(mModelId));
        mNote.setField(0, field);
        Intent editCard = new Intent(this, MultimediaEditFieldActivity.class);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD_INDEX, 0);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD, field);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_WHOLE_NOTE, mNote);
        startActivityForResultWithoutAnimation(editCard, REQUEST_MULTIMEDIA_EDIT);
    }


    private boolean checkCollectionHasLoaded(@StringRes int resId) {
        if (mModelId == 0 || !mHasLoadedCol) {
            //Haven't loaded yet.
            UIUtils.showThemedToast(this, getString(resId), false);
            return false;
        }
        return true;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (data == null) {
            Timber.d("data was null");
            return;
        }
        if (requestCode == REQUEST_MULTIMEDIA_EDIT) {

            if (resultCode != RESULT_OK) {
                return;
            }
            if (data.getExtras() == null) {
                return;
            }
            IField field = (IField) data.getExtras().get(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD);

            if (field == null) {
                return;
            }

            if (!registerMediaForWebView(field.getImagePath())) {
                return;
            }
            if (!registerMediaForWebView(field.getAudioPath())) {
                return;
            }

            this.mWebView.pasteHtml(field.getFormattedValue());
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @CheckResult
    private boolean registerMediaForWebView(String imagePath) {
        if (imagePath == null) {
            //Nothing to register - continue with execution.
            return true;
        }

        //TODO: this is a little too early, ideally should be in a temp file which can't be cleared until we exit.
        Timber.i("Adding media to collection: %s", imagePath);
        File f = new File(imagePath);
        try {
            getCol().getMedia().addFile(f);
            return true;
        } catch (IOException e) {
            Timber.e(e, "Failed to add file");
            return false;
        }
    }


    private void setupWebView(VisualEditorWebView webView) {
        WebViewDebugging.initializeDebugging(AnkiDroidApp.getSharedPrefs(this));


        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        CardAppearance cardAppearance = CardAppearance.create(new ReviewerCustomFonts(this), preferences);
        String css = cardAppearance.getStyle();
        mWebView.injectCss(css);
        webView.injectCss(css);
        webView.setOnTextChangeListener(s -> this.mCurrentText = s);
        webView.setSelectionChangedListener(this::handleSelectionChanged);

        webView.setHtml(mField.getText());
        //reset the note history so we can't undo the above action.
        webView.execFunction("clearHistory");

        //Could be better, this is done per card in AbstractFlashCardViewer
        webView.getSettings().setDefaultFontSize(CardAppearance.calculateDynamicFontSize(mField.getText()));
    }


    private void handleSelectionChanged(SelectionType selectionType) {
        SelectionType previousSelectionType = this.mSelectionType;

        this.mSelectionType = selectionType;
        if (selectionType != previousSelectionType) {
            invalidateOptionsMenu();
        }
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
        try {
            initWebView(col);
        } catch (IOException e) {
            Timber.e(e, "Failed to init web view");
            failStartingVisualEditor();
            return;
        }

        JSONObject model = col.getModels().get(mModelId);
        String css = getModelCss(model);
        if (Models.isCloze(model)) {
            Timber.d("Cloze detected. Enabling Cloze button");
            findViewById(R.id.editor_button_cloze).setVisibility(View.VISIBLE);
        }

        mWebView.injectCss(css);
        mHasLoadedCol = true;
    }


    private String getModelCss(JSONObject model) {
        try {
            String css = model.getString("css");
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


    private void initWebView(Collection col) throws IOException {
        String mBaseUrl = Utils.getBaseUrl(col.getMedia().dir());
        String assetAsString = mAssetReader.loadAsUtf8String("visualeditor/visual_editor.html");
        mWebView.init(assetAsString, mBaseUrl);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // NOTE: This is called every time the selection is changed to a new element type.
        int menuResource = getSelectionMenuOptions();
        //I decided it was best not to show "save/undo" while an image is visible, as it confuses the meaning of save.
        //If we want so in the future, add another inflate call here.
        getMenuInflater().inflate(menuResource, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /** Obtains an additional options menu for the current selection */
    @CheckResult
    private @MenuRes int getSelectionMenuOptions() {
        switch (mSelectionType) {
            case IMAGE:
                Timber.i("Displaying Image Options Menu");
                return R.menu.visual_editor_image;
            case REGULAR:
                Timber.i("Displaying Regular Options Menu");
                return R.menu.visual_editor;
            default:
                Timber.w("Unknown Options Menu type: '%s'. Displaying Regular Menu", mSelectionType);
                return R.menu.visual_editor;
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SelectionType selectionType = this.mSelectionType;
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
                return onSpecificOptionsItemSelected(item, selectionType);
        }
    }


    private boolean onSpecificOptionsItemSelected(MenuItem item, SelectionType selectionType) {
        //CODE DESIGN: unsure if we want a if () .. return, or handle calling the superclass in the method
        //so we just have a return.
        switch (selectionType) {
            case IMAGE:
                if (imageOptionsItemSelected(item, selectionType)) {
                    return true;
                }
                break;
            case REGULAR:
                return super.onOptionsItemSelected(item);
            default:

        }
        return super.onOptionsItemSelected(item);
    }


    private boolean imageOptionsItemSelected(MenuItem item, SelectionType selectionType) {
        switch (item.getItemId()) {
            case R.id.action_image_delete:
                deleteSelectedImage(selectionType);
                return true;
            default:
                return false;
        }
    }


    private void deleteSelectedImage(SelectionType selectionType) {
        mWebView.deleteImage(selectionType.getGuid());
        resetSelectionType();
    }


    /** HACK: Resets the selection type when the UI doesn't fire an appropriate event */
    private void resetSelectionType() {
        mSelectionType = SelectionType.REGULAR;
        invalidateOptionsMenu();
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
