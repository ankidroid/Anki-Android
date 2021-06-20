package com.ichi2.anki.multimediacard.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DrawingActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.RegisterMediaForWebView;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.dialogs.DiscardChangesDialog;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.fields.AudioRecordingField;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.fields.TextField;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorToolbar;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView;
import com.ichi2.anki.multimediacard.visualeditor.WebViewUndoRedo;
import com.ichi2.anki.noteeditor.CustomToolbarButton;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView.SelectionType;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.anki.web.MathJaxUtils;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;
import com.ichi2.utils.AssetReader;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.LargeObjectStorage;
import com.ichi2.utils.LargeObjectStorage.StorageData;
import com.ichi2.utils.LargeObjectStorage.StorageKey;
import com.ichi2.utils.WebViewDebugging;
import com.mrudultora.colorpicker.ColorPickerDialog;
import com.mrudultora.colorpicker.listeners.OnSelectColorListener;
import com.mrudultora.colorpicker.util.ColorItemShape;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;
import timber.log.Timber;

import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.*;

import static com.ichi2.anki.NoteEditor.REQUEST_MULTIMEDIA_EDIT;

//NOTE: Remove formatting on "{{c1::" will cause a failure to detect the cloze deletion, this is the same as Anki.
public class VisualEditorActivity extends AnkiActivity {

    private static final int COLOR_PICKER_FOREGROUND = 1;
    private static final int COLOR_PICKER_BACKGROUND = 2;
    private static final int REQUEST_WHITE_BOARD_EDIT = 3;

    public static final String EXTRA_FIELD = "visual.card.ed.extra.current.field";
    public static final String EXTRA_FIELD_INDEX = "visual.card.ed.extra.current.field.index";
    /** The Id of the current model (long) */
    public static final String EXTRA_MODEL_ID = "visual.card.ed.extra.model.id";
    /** All fields in a (string[])  */
    public static final String EXTRA_ALL_FIELDS = "visual.card.ed.extra.all.fields";

    public static final StorageKey<String> STORAGE_CURRENT_FIELD = new StorageKey<>(
            "visual.card.ed.extra.current.field",
            "visualed_current_field",
            "bin");

    public static final StorageKey<String[]> STORAGE_EXTRA_FIELDS = new StorageKey<>(
            "visual.card.ed.extra.extra.fields",
            "visualed_extra_fields",
            "bin");


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
    private LargeObjectStorage mLargeObjectStorage = new LargeObjectStorage(this);
    private RegisterMediaForWebView mRegisterMediaForWebView;

    private WebViewUndoRedo mWebViewUndoRedo;

    private final List<View> mCustomButtons = new ArrayList<>();
    private Paint mStringPaint;

    private VisualEditorToolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.visual_editor);

        if (!setFieldsOnStartup(savedInstanceState)) {
            failStartingVisualEditor();
            return;
        }

        setupWebView(mWebView);

        setupEditorScrollbarButtons(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        mRegisterMediaForWebView = new RegisterMediaForWebView(this);
        startLoadingCollection();
    }

    private void saveChangesOrExit() {
        if (hasChanges()) {
            DiscardChangesDialog.getDefault(this)
                    .onPositive((dialog, which) -> this.finishCancel())
                    .build().show();
        } else {
            this.finishCancel();
        }
    }

    public void setCurrentText(String currentText) {
        mCurrentText = currentText;
    }

    public String getCurrentText() {
        return mCurrentText;
    }



    private boolean hasChanges() {
        try {
            return !mCurrentText.equals(mFields[mIndex]);
        } catch (Exception e) {
            Timber.w(e, "Failed to determine if editor has changes. Assuming true.");
            return true;
        }
    }


    @Override
    public void onBackPressed() {
        saveChangesOrExit();
        //explicitly do not call super.onBackPressed()
    }

    @Override
    protected boolean onActionBarBackPressed() {
        saveChangesOrExit();
        return true;
    }


    private void setupEditorScrollbarButtons(Context context) {
        Resources resources = context.getResources();
        SimpleListenerSetup setupAction = (id, functionality, tooltipStringResource) -> {
            String jsName = mWebView.getJsFunctionName(functionality);
            if (jsName == null) {
                Timber.d("Skipping functionality: %s", functionality);
            }
            View view = findViewById(id);
            view.setOnClickListener(v -> mWebView.execFunction(jsName));
            setTooltip(view, resources.getString(tooltipStringResource));
        };

        int paintSize = dpToPixels(28);

        mStringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStringPaint.setTextSize(paintSize);
        mStringPaint.setColor(Color.BLACK);
        mStringPaint.setTextAlign(Paint.Align.CENTER);

        mToolbar = findViewById(R.id.editor_toolbar);

        updateCustomButtons();


        setupAction.apply(R.id.editor_button_bold, BOLD, R.string.visual_editor_tooltip_bold);
        setupAction.apply(R.id.editor_button_italic, ITALIC, R.string.visual_editor_tooltip_italic);
        setupAction.apply(R.id.editor_button_underline, UNDERLINE, R.string.visual_editor_tooltip_underline);
        setupAction.apply(R.id.editor_button_clear_formatting, CLEAR_FORMATTING, R.string.visual_editor_tooltip_clear_formatting);
        setupAction.apply(R.id.editor_button_list_bullet, UNORDERED_LIST, R.string.visual_editor_tooltip_bullet_list);
        setupAction.apply(R.id.editor_button_list_numbered, ORDERED_LIST, R.string.visual_editor_tooltip_numbered_list);
        setupAction.apply(R.id.editor_button_horizontal_rule, HORIZONTAL_RULE, R.string.visual_editor_tooltip_horizontal_line);
        setupAction.apply(R.id.editor_button_align_left, ALIGN_LEFT, R.string.visual_editor_tooltip_align_left);
        setupAction.apply(R.id.editor_button_align_center, ALIGN_CENTER, R.string.visual_editor_tooltip_align_center);
        setupAction.apply(R.id.editor_button_align_right, ALIGN_RIGHT, R.string.visual_editor_tooltip_align_right);
        setupAction.apply(R.id.editor_button_align_justify, ALIGN_JUSTIFY, R.string.visual_editor_tooltip_align_justify);
        setupAction.apply(R.id.editor_button_view_html, EDIT_SOURCE, R.string.visual_editor_tooltip_view_source); //this is a toggle.

        AndroidListenerSetup setupAndroidListener = (id, function, tooltipId) -> {
            View view = findViewById(id);
            view.setOnClickListener(v -> function.run());
            setTooltip(view, resources.getString(tooltipId));
        };

        setupAndroidListener.apply(R.id.editor_button_customs, this::displayAddToolbarDialog, R.string.visual_editor_add_custom_buttons);
        setupAndroidListener.apply(R.id.editor_button_white_board, this::openWhiteBoard, R.string.visual_editor_white_board); // opens drawingActivity
        setupAndroidListener.apply(R.id.editor_button_cloze, this::performCloze, R.string.visual_editor_tooltip_cloze);
        setupAndroidListener.apply(R.id.editor_button_insert_mathjax, this::insertMathJax, R.string.visual_editor_tooltip_mathjax);
        setupAndroidListener.apply(R.id.editor_button_add_image, this::openAdvancedViewerForAddImage, R.string.visual_editor_tooltip_add_image);
        setupAndroidListener.apply(R.id.editor_button_record_audio, this::openAdvancedViewerForRecordAudio, R.string.visual_editor_tooltip_record_audio);
        setupAndroidListener.apply(R.id.editor_button_text_color, () -> this.openColorPicker(COLOR_PICKER_FOREGROUND, Color.BLACK), R.string.visual_editor_tooltip_text_color);
        setupAndroidListener.apply(R.id.editor_button_background_color, () -> this.openColorPicker(COLOR_PICKER_BACKGROUND, Color.YELLOW), R.string.visual_editor_tooltip_background_color);
    }

    // todo extract toolbar code to VisualEditorToolbar
    private void updateCustomButtons() {

        ArrayList<CustomToolbarButton> buttons = getToolbarButtons();

        clearCustomItems();

        for (CustomToolbarButton b : buttons) {

            // 0th button shows as '1' and is Ctrl + 1
            int visualIndex = b.getIndex() + 1;
            String text = Integer.toString(visualIndex);
            Drawable bmp = createDrawableForString(text);

            AppCompatImageButton v = new AppCompatImageButton(this);
            v.setBackgroundDrawable(bmp);
            mCustomButtons.add(v);
            mToolbar.addView(v);

            // Allow Ctrl + 1...Ctrl + 0 for item 10.
            v.setTag(Integer.toString(visualIndex % 10));

            v.setOnClickListener(view -> {
                mWebView.insertCustomTag(b.getPrefix(), b.getSuffix());
            });

            v.setOnLongClickListener(discard -> {
                suggestRemoveButton(b);
                return true;
            });
        }
    }

    @NonNull
    public View insertItem(int id, Drawable drawable, com.ichi2.anki.noteeditor.Toolbar.TextFormatter formatter) {
        return insertItem(id, drawable, () -> onFormat(formatter));
    }


    private void onFormat(com.ichi2.anki.noteeditor.Toolbar.TextFormatter formatter) {
        mWebView.pasteHtml(formatter.toString());
    }


    @NonNull
    public AppCompatImageButton insertItem(@IdRes int id, Drawable drawable, Runnable runnable) {
        Context context = this;
        AppCompatImageButton button = new AppCompatImageButton(context);
        button.setId(id);
        button.setBackgroundDrawable(drawable);

        /*
            Style didn't work
            int buttonStyle = R.style.note_editor_toolbar_button;
            ContextThemeWrapper context = new ContextThemeWrapper(getContext(), buttonStyle);
            AppCompatImageButton button = new AppCompatImageButton(context, null, buttonStyle);
        */

        // apply style
        int marginEnd = (int) Math.ceil(8 / context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMarginEnd(marginEnd);
        button.setLayoutParams(params);


        int fourDp = (int) Math.ceil(4 / context.getResources().getDisplayMetrics().density);

        button.setPadding(fourDp, fourDp, fourDp, fourDp);
        // end apply style


        this.mToolbar.addView(button, mToolbar.getChildCount());
        mCustomButtons.add(button);
        button.setOnClickListener(l -> runnable.run());

        // Hack - items are truncated from the scrollview
        View v = findViewById(R.id.editor_toolbar_internal);

        int expectedWidth = getVisibleItemCount() * dpToPixels(48 + 2 * 4); //width + 4dp padding on both sides
        int width = getScreenWidth();
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(v.getLayoutParams());
        p.gravity = Gravity.CENTER_VERTICAL | ((expectedWidth > width) ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        v.setLayoutParams(p);

        return button;
    }

    protected int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager()
                .getDefaultDisplay()
                .getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    private int getVisibleItemCount() {
        int count = 0;
        for (int i = 0; i < mToolbar.getChildCount(); i++) {
            if (mToolbar.getChildAt(i).getVisibility() == View.VISIBLE){
                count++;
            }
        }
        return count;
    }

    public void clearCustomItems() {
        for (View v : mCustomButtons) {
            mToolbar.removeView(v);
        }
        mCustomButtons.clear();
    }


    private void displayAddToolbarDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.add_toolbar_item)
                .customView(R.layout.note_editor_toolbar_add_custom_item, true)
                .positiveText(R.string.dialog_positive_create)
                .neutralText(R.string.help)
                .negativeText(R.string.dialog_cancel)
                .onNeutral((m, v) -> openUrl(Uri.parse(getString(R.string.link_manual_note_format_toolbar))))
                .onPositive((m, v) -> {
                    View view = m.getView();
                    EditText et =  view.findViewById(R.id.note_editor_toolbar_before);
                    EditText et2 = view.findViewById(R.id.note_editor_toolbar_after);

                    addToolbarButton(et.getText().toString(), et2.getText().toString());
                })
                .show();
    }

    private void addToolbarButton(String prefix, String suffix) {
        if (TextUtils.isEmpty(prefix) && TextUtils.isEmpty(suffix)) {
            return;
        }

        ArrayList<CustomToolbarButton> toolbarButtons = getToolbarButtons();

        toolbarButtons.add(new CustomToolbarButton(toolbarButtons.size(), prefix, suffix));
        saveToolbarButtons(toolbarButtons);

        updateCustomButtons();
    }



    private void suggestRemoveButton(CustomToolbarButton button) {
        new MaterialDialog.Builder(this)
                .title(R.string.remove_toolbar_item)
                .positiveText(R.string.dialog_positive_delete)
                .negativeText(R.string.dialog_cancel)
                .onPositive((dialog, action) -> removeButton(button))
                .show();
    }

    private void removeButton(CustomToolbarButton button) {
        ArrayList<CustomToolbarButton> toolbarButtons = getToolbarButtons();

        toolbarButtons.remove(button.getIndex());

        saveToolbarButtons(toolbarButtons);
        updateCustomButtons();
    }

    private void saveToolbarButtons(ArrayList<CustomToolbarButton> buttons) {
        AnkiDroidApp.getSharedPrefs(this).edit()
                .putStringSet("visual_editor_custom_buttons", CustomToolbarButton.toStringSet(buttons))
                .apply();
    }

    private int dpToPixels(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    @NonNull
    public Drawable createDrawableForString(String text) {
        float baseline = -mStringPaint.ascent();
        int size = (int) (baseline + mStringPaint.descent() + 0.5f);

        Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);

        canvas.drawText(text, size /2f, baseline, mStringPaint);

        return new BitmapDrawable(getResources(), image);
    }


    @NonNull
    private ArrayList<CustomToolbarButton> getToolbarButtons() {
        Set<String> set = AnkiDroidApp.getSharedPrefs(this).getStringSet("visual_editor_custom_buttons", new HashSet<>(0));
        return CustomToolbarButton.fromStringSet(set);
    }


    private void openWhiteBoard() {
        startActivityForResultWithoutAnimation(new Intent(this, DrawingActivity.class), REQUEST_WHITE_BOARD_EDIT);
    }

    private void insertMathJax() {
        mWebView.pasteHtml(MathJaxUtils.getMathJaxInsertionString());
    }


    private void setTooltip(View view, String tooltip) {
        TooltipCompat.setTooltipText(view, tooltip);
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
        if (resultCode != RESULT_OK) {
            return;
        }
        if (data.getExtras() == null) {
            return;
        }
        switch (requestCode) {
            case REQUEST_MULTIMEDIA_EDIT:
                IField field = (IField) data.getExtras().get(MultimediaEditFieldActivity.EXTRA_RESULT_FIELD);

                if (field == null) {
                    return;
                }

                if (!mRegisterMediaForWebView.registerMediaForWebView(field.getImagePath())) {
                    return;
                }
                if (!mRegisterMediaForWebView.registerMediaForWebView(field.getAudioPath())) {
                    return;
                }

                this.mWebView.pasteHtml(field.getFormattedValue());
                break;
            case REQUEST_WHITE_BOARD_EDIT:
                // receive image from drawing activity
                Uri uri = (Uri) data.getExtras().get(DrawingActivity.EXTRA_RESULT_WHITEBOARD);
                try {
                    this.mWebView.pasteHtml(mRegisterMediaForWebView.onImagePaste(uri));
                } catch (NullPointerException e) {
                    Timber.w(e);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void setupWebView(VisualEditorWebView webView) {
        WebViewDebugging.initializeDebugging(AnkiDroidApp.getSharedPrefs(this));


        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(this);
        CardAppearance cardAppearance = CardAppearance.create(new ReviewerCustomFonts(this), preferences);
        String css = cardAppearance.getStyle();
        webView.injectCss(css);
        webView.setSelectionChangedListener(this::handleSelectionChanged);

        mWebViewUndoRedo = new WebViewUndoRedo(this, webView);
        mWebViewUndoRedo.setContent(mCurrentText);

        //Could be better, this is done per card in AbstractFlashCardViewer
        webView.getSettings().setDefaultFontSize(CardAppearance.calculateDynamicFontSize(mCurrentText));

        webView.setNightMode(cardAppearance.isNightMode(), Themes.getCurrentTheme(this));
    }


    private void handleSelectionChanged(SelectionType selectionType) {
        SelectionType previousSelectionType = this.mSelectionType;

        this.mSelectionType = selectionType;
        if (selectionType != previousSelectionType) {
            invalidateOptionsMenu();
        }
    }


    private boolean setFieldsOnStartup(Bundle savedInstanceState) {

        tryDeserializeSavedState(savedInstanceState);
        Bundle extras = this.getIntent().getExtras();
        if (extras == null) {
            Timber.w("No Extras in Bundle");
            return false;
        }

        if (mCurrentText == null) {
            mCurrentText = mLargeObjectStorage.getSingleInstance(STORAGE_CURRENT_FIELD, extras);
        }
        Integer index = (Integer) extras.getSerializable(VisualEditorActivity.EXTRA_FIELD_INDEX);

        if (mFields == null) {
            this.mFields = mLargeObjectStorage.getSingleInstance (STORAGE_EXTRA_FIELDS, extras);
        }
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


    private void tryDeserializeSavedState(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        if (mLargeObjectStorage.hasKey(STORAGE_CURRENT_FIELD, savedInstanceState)) {
            mCurrentText = mLargeObjectStorage.getSingleInstance(STORAGE_CURRENT_FIELD, savedInstanceState);
        }
        if (mLargeObjectStorage.hasKey(STORAGE_EXTRA_FIELDS, savedInstanceState)) {
            mFields = mLargeObjectStorage.getSingleInstance(STORAGE_EXTRA_FIELDS, savedInstanceState);
        }
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

        // invisible redo/undo button if no history
        boolean canShowRedo = mWebViewUndoRedo == null || mWebViewUndoRedo.getCanRedo();
        menu.findItem(R.id.action_redo).setVisible(canShowRedo);

        boolean canShowUndo = mWebViewUndoRedo == null || mWebViewUndoRedo.getCanUndo();
        menu.findItem(R.id.action_undo).setVisible(canShowUndo);

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
        int itemId = item.getItemId();
        if (itemId == R.id.action_save) {
            Timber.i("Save button pressed");
            finishWithSuccess();
            return true;
        } else if (itemId == R.id.action_cut) {// document.execCommand("cut", ...) does not work for images, so we need to implement our own function.
            // "Cut" does not appear on the CAB when selecting only an image in a contentEditable,
            // but we can still cut via our JavaScript function, therefore we have the button in the ActionBar
            // COULD_BE_BETTER: See if we can optionally add this functionality back into the CAB
            // DEFECT: I was unable to disable the onClick menu for images provided by summernote, this makes it
            // annoying for the user as the custom image UI get in the way. This is because I'm unfamiliar with JS
            cut();
            return true;
        } else if (itemId == R.id.action_redo) {
            mWebViewUndoRedo.redo();
            return true;
        } else if  (itemId == R.id.action_undo) {
            mWebViewUndoRedo.undo();
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

    private void cut() {
        mWebView.execFunction("cut");
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


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        storeDataNoException(outState, STORAGE_CURRENT_FIELD.asData(mCurrentText));
        storeDataNoException(outState, STORAGE_EXTRA_FIELDS.asData(mFields));
    }


    private <T extends Serializable> void storeDataNoException(@NonNull Bundle outState, StorageData<T> data) {
        try {
            mLargeObjectStorage.storeSingleInstance(data, outState);
        } catch (Exception e) {
            Timber.e(e, "failed to store '%s'", STORAGE_CURRENT_FIELD.getBundleKey());
        }
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


    /** Setup a button which executes a JavaScript runnable */
    @FunctionalInterface
    protected interface SimpleListenerSetup {
        void apply(@IdRes int buttonId, VisualEditorFunctionality function, @StringRes int tooltipText);
    }

    /** A button which performs an Android Runnable */
    @FunctionalInterface
    protected interface AndroidListenerSetup {
        void apply(@IdRes int buttonId, Runnable function, @StringRes int tooltipText);
    }
}
