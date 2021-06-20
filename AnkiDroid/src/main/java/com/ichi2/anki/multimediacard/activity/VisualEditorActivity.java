/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimediacard.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.DrawingActivity;
import com.ichi2.anki.MediaRegistration;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.cardviewer.CardAppearance;
import com.ichi2.anki.dialogs.DiscardChangesDialog;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.TextField;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorToolbar;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView;
import com.ichi2.anki.multimediacard.visualeditor.VisualEditorWebView.SelectionType;
import com.ichi2.anki.multimediacard.visualeditor.WebViewUndoRedo;
import com.ichi2.anki.reviewer.ReviewerCustomFonts;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;
import com.ichi2.utils.AssetReader;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.LargeObjectStorage;
import com.ichi2.utils.LargeObjectStorage.StorageData;
import com.ichi2.utils.LargeObjectStorage.StorageKey;
import com.ichi2.utils.WebViewDebugging;

import java.io.IOException;
import java.io.Serializable;

import androidx.annotation.CheckResult;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import timber.log.Timber;

import static com.ichi2.anki.NoteEditor.REQUEST_MULTIMEDIA_EDIT;

//NOTE: Remove formatting on "{{c1::" will cause a failure to detect the cloze deletion, this is the same as Anki.
public class VisualEditorActivity extends AnkiActivity {

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
    private final AssetReader mAssetReader = new AssetReader(this);
    //Unsure if this is needed, or whether getCol will block until onCollectionLoaded completes.
    private boolean mHasLoadedCol;
    private final LargeObjectStorage mLargeObjectStorage = new LargeObjectStorage(this);
    private MediaRegistration mMediaRegistration;

    private WebViewUndoRedo mWebViewUndoRedo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.visual_editor);

        if (!setFieldsOnStartup(savedInstanceState)) {
            failStartingVisualEditor();
            return;
        }

        setupWebView(mWebView);

        VisualEditorToolbar visualEditorToolbar = findViewById(R.id.editor_toolbar);
        visualEditorToolbar.setEditorScrollBarButtons(this, mWebView);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            enableToolbar(toolbar);
        }

        mMediaRegistration = new MediaRegistration(this);
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

    public int getIndex() {
        return mIndex;
    }


    public long getModelId() {
        return mModelId;
    }


    public String[] getFields() {
        return mFields;
    }


    public boolean hasLoadedCol() {
        return mHasLoadedCol;
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

                if (!mMediaRegistration.registerMediaForWebView(field.getImagePath())) {
                    return;
                }
                if (!mMediaRegistration.registerMediaForWebView(field.getAudioPath())) {
                    return;
                }

                this.mWebView.pasteHtml(field.getFormattedValue());
                break;
            case REQUEST_WHITE_BOARD_EDIT:
                // receive image from drawing activity
                Uri uri = (Uri) data.getExtras().get(DrawingActivity.EXTRA_RESULT_WHITEBOARD);
                try {
                    this.mWebView.pasteHtml(mMediaRegistration.onImagePaste(uri));
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
        mFields[mIndex] = mCurrentText;

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
        UIUtils.showThemedToast(this, R.string.visual_editor_failed, false);
        finishCancel();
    }


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        Timber.d("onCollectionLoaded");
        try {
            initWebView(col);
        } catch (IOException e) {
            Timber.w(e, "Failed to init web view");
            failStartingVisualEditor();
            return;
        }

        JSONObject model = col.getModels().get(mModelId);
        String css = getModelCss(model);
        if (model != null && Models.isCloze(model)) {
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
        String baseUrl = Utils.getBaseUrl(col.getMedia().dir());
        String assetAsString = mAssetReader.loadAsUtf8String("visualeditor/visual_editor.html");
        mWebView.init(assetAsString, baseUrl);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // NOTE: This is called every time the selection is changed to a new element type.
        int menuResource = getSelectionMenuOptions();
        //I decided it was best not to show "save/undo" while an image is visible, as it confuses the meaning of save.
        //If we want so in the future, add another inflate call here.
        getMenuInflater().inflate(menuResource, menu);

        // todo redo undo problem here, null values
        // invisible redo/undo button if no history
        // boolean canShowRedo = mWebViewUndoRedo == null || mWebViewUndoRedo.getCanRedo();
        // menu.findItem(R.id.action_redo).setVisible(canShowRedo);

        // boolean canShowUndo = mWebViewUndoRedo == null || mWebViewUndoRedo.getCanUndo();
        // menu.findItem(R.id.action_undo).setVisible(canShowUndo);

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
}
