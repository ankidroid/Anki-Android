package com.ichi2.anki.multimediacard.activity;

import android.content.Intent;
import android.graphics.Color;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView.SelectionType;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.exception.EmptyMediaException;
import com.ichi2.utils.AssetReader;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.WebViewDebugging;
import com.mrudultora.colorpicker.ColorPickerDialog;
import com.mrudultora.colorpicker.listeners.OnSelectColorListener;
import com.mrudultora.colorpicker.util.ColorItemShape;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.util.Locale;

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import timber.log.Timber;

import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.*;

import static com.ichi2.anki.NoteEditor.REQUEST_MULTIMEDIA_EDIT;

//NOTE: Remove formatting on "{{c1::" will cause a failure to detect the cloze deletion, this is the same as Anki.
public class VisualEditorActivity extends AnkiActivity {

    private static final int COLOR_PICKER_FOREGROUND = 1;
    private static final int COLOR_PICKER_BACKGROUND = 2;

    public static final String EXTRA_FIELD = "visual.card.ed.extra.current.field";
    public static final String EXTRA_FIELD_INDEX = "visual.card.ed.extra.current.field.index";
    /** The Id of the current model (long) */
    public static final String EXTRA_MODEL_ID = "visual.card.ed.extra.model.id";
    /** All fields in a (string[])  */
    public static final String EXTRA_ALL_FIELDS = "visual.card.ed.extra.all.fields";

    private String mCurrentText;
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
        SimpleListenerSetup setupAction = (id, functionality) -> {
            String jsName = mWebView.getJsFunctionName(functionality);
            if (jsName == null) {
                Timber.d("Skipping functionality: %s", functionality);
            }
            View view = findViewById(id);
            view.setOnClickListener(v -> mWebView.execFunction(jsName));
        };

        setupAction.apply(R.id.editor_button_bold, BOLD);
        setupAction.apply(R.id.editor_button_italic, ITALIC);
        setupAction.apply(R.id.editor_button_underline, UNDERLINE);
        setupAction.apply(R.id.editor_button_clear_formatting, CLEAR_FORMATTING);
        setupAction.apply(R.id.editor_button_list_bullet, UNORDERED_LIST);
        setupAction.apply(R.id.editor_button_list_numbered, ORDERED_LIST);
        setupAction.apply(R.id.editor_button_horizontal_rule, HORIZONTAL_RULE);
        setupAction.apply(R.id.editor_button_align_left, ALIGN_LEFT);
        setupAction.apply(R.id.editor_button_align_center, ALIGN_CENTER);
        setupAction.apply(R.id.editor_button_align_right, ALIGN_RIGHT);
        setupAction.apply(R.id.editor_button_align_justify, ALIGN_JUSTIFY);
        setupAction.apply(R.id.editor_button_view_html, EDIT_SOURCE); //this is a toggle.

        findViewById(R.id.editor_button_add_image).setOnClickListener(v -> this.openAdvancedViewerForAddImage());

        findViewById(R.id.editor_button_text_color).setOnClickListener(v -> this.openColorPicker(COLOR_PICKER_FOREGROUND, Color.BLACK));
        findViewById(R.id.editor_button_background_color).setOnClickListener(v -> this.openColorPicker(COLOR_PICKER_BACKGROUND, Color.YELLOW));

        findViewById(R.id.editor_button_cloze).setOnClickListener(v -> performCloze());
    }


    private void openColorPicker(int dialogId, Integer defaultColor) {
        ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this);   // Pass the context.
        colorPickerDialog.setColors()
                .setColumns(5)                        		// Default number of columns is 5.
                .setDefaultSelectedColor(defaultColor)		// By default no color is used.
                .setColorItemShape(ColorItemShape.SQUARE)
                .setOnSelectColorListener(new OnSelectColorListener() {
                    @Override
                    public void onColorSelected(int color, int position) {
                        if (dialogId == COLOR_PICKER_FOREGROUND) {
                            mWebView.setSelectedTextColor(color);
                        } else if (dialogId == COLOR_PICKER_BACKGROUND) {
                            mWebView.setSelectedBackgroundColor(color);
                        }
                    }
                    @Override
                    public void cancel() {
                        colorPickerDialog.dismissDialog();	// Dismiss the dialog.
                    }
                }).show();
        colorPickerDialog.getPositiveButton().setTextSize(TypedValue.COMPLEX_UNIT_SP,14f);
        colorPickerDialog.getNegativeButton().setTextSize(TypedValue.COMPLEX_UNIT_SP,14f);
    }


    private void performCloze() {
        mWebView.insertCloze(getNextClozeId());
    }


    private int getNextClozeId() {
        List<String> fields = Arrays.asList(mFields);
        fields.set(mIndex, mCurrentText);
        return Note.ClozeUtils.getNextClozeIndex(fields);
    }


    private void openAdvancedViewerForAddImage() {
        //TODO; Copied from NoteEditor
        if (mModelId == 0 || !mHasLoadedCol) {
            //Haven't loaded yet.
            UIUtils.showThemedToast(this, "Unable to add image", false);
            return;
        }

        IField field = new ImageField();
        IMultimediaEditableNote mNote = NoteService.createEmptyNote(getCol().getModels().get(mModelId));
        mNote.setField(0, field);
        Intent editCard = new Intent(this, MultimediaEditFieldActivity.class);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD_INDEX, 0);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD, field);
        editCard.putExtra(MultimediaEditFieldActivity.EXTRA_WHOLE_NOTE, mNote);
        startActivityForResultWithoutAnimation(editCard, REQUEST_MULTIMEDIA_EDIT);
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

            this.mWebView.pasteHtml(field.getFormattedValue());
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressWarnings( {"BooleanMethodIsAlwaysInverted", "RedundantSuppression"})
    @CheckResult
    private boolean registerMediaForWebView(String imagePath) {
        //TODO: this is a little too early, ideally should be in a temp file which can't be cleared until we exit.
        Timber.i("Adding media to collection: %s", imagePath);
        File f = new File(imagePath);
        try {
            getCol().getMedia().addFile(f);
            return true;
        } catch (IOException | EmptyMediaException e) {
            Timber.e(e, "Failed to add file");
            return false;
        }
    }


    private void setupWebView(VisualEditorWebView webView) {
        WebViewDebugging.initializeDebugging(AnkiDroidApp.getSharedPrefs(this));


        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        CardAppearance cardAppearance = CardAppearance.create(new ReviewerCustomFonts(this), preferences);
        String css = cardAppearance.getStyle();
        webView.injectCss(css);
        webView.setOnTextChangeListener(s -> this.mCurrentText = s);
        webView.setSelectionChangedListener(this::handleSelectionChanged);

        webView.setHtml(mCurrentText);

        webView.load();

        //Could be better, this is done per card in AbstractFlashCardViewer
        webView.getSettings().setDefaultFontSize(CardAppearance.calculateDynamicFontSize(mCurrentText));
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
        mCurrentText = (String) extras.getSerializable(VisualEditorActivity.EXTRA_FIELD);
        Integer index = (Integer) extras.getSerializable(VisualEditorActivity.EXTRA_FIELD_INDEX);

        this.mFields = (String[]) extras.getSerializable(VisualEditorActivity.EXTRA_ALL_FIELDS);
        Long modelId = (Long) extras.getSerializable(VisualEditorActivity.EXTRA_MODEL_ID);

        if (mCurrentText == null) {
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
        mWebView.insertCloze(clozeId);
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
        if (item.getItemId() == R.id.action_save) {
            Timber.i("Save button pressed");
            finishWithSuccess();
            return true;
        }
        return onSpecificOptionsItemSelected(item, selectionType);
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
        if (item.getItemId() == R.id.action_image_delete) {
            deleteSelectedImage(selectionType);
            return true;
        }
        return false;
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
        IField f = new TextField();
        f.setText(mCurrentText);
        Intent resultData = new Intent();
        resultData.putExtra(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD, f);
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
        void apply(@IdRes int buttonId, VisualEditorFunctionality function);
    }
}
