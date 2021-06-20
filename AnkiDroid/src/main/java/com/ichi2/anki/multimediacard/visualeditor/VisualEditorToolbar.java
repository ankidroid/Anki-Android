/*
 *  Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>
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


package com.ichi2.anki.multimediacard.visualeditor;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DrawingActivity;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.anki.multimediacard.IMultimediaEditableNote;
import com.ichi2.anki.multimediacard.activity.MultimediaEditFieldActivity;
import com.ichi2.anki.multimediacard.activity.VisualEditorActivity;
import com.ichi2.anki.multimediacard.fields.AudioRecordingField;
import com.ichi2.anki.multimediacard.fields.IField;
import com.ichi2.anki.multimediacard.fields.ImageField;
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote;
import com.ichi2.anki.noteeditor.CustomToolbarButton;
import com.ichi2.anki.servicelayer.NoteService;
import com.ichi2.anki.web.MathJaxUtils;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.mrudultora.colorpicker.ColorPickerDialog;
import com.mrudultora.colorpicker.listeners.OnSelectColorListener;
import com.mrudultora.colorpicker.util.ColorItemShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.TooltipCompat;
import timber.log.Timber;

import static com.ichi2.anki.NoteEditor.REQUEST_MULTIMEDIA_EDIT;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.ALIGN_CENTER;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.ALIGN_JUSTIFY;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.ALIGN_LEFT;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.ALIGN_RIGHT;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.BOLD;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.CLEAR_FORMATTING;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.EDIT_SOURCE;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.HORIZONTAL_RULE;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.ITALIC;
import static com.ichi2.anki.multimediacard.visualeditor.VisualEditorFunctionality.UNDERLINE;

// currently only used for adding new view to linearLayout of VisualEditorToolbar
public class VisualEditorToolbar extends LinearLayoutCompat {

    VisualEditorActivity mVisualEditorActivity;
    VisualEditorWebView mWebView;

    private final List<View> mCustomButtons = new ArrayList<>();
    private Paint mStringPaint;

    private int mForeGroundSelectedColor = Color.BLACK;
    private int mBackGroundSelectedColor = Color.YELLOW;

    private static final int COLOR_PICKER_FOREGROUND = 1;
    private static final int COLOR_PICKER_BACKGROUND = 2;
    public static final int REQUEST_WHITE_BOARD_EDIT = 3;

    public VisualEditorToolbar(Context context) {
        super(context);
    }

    public VisualEditorToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VisualEditorToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    public void setEditorScrollBarButtons(VisualEditorActivity ankiActivity, VisualEditorWebView webView) {
        this.mWebView = webView;
        this.mVisualEditorActivity = ankiActivity;
        int paintSize = dpToPixels(28);

        mStringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStringPaint.setTextSize(paintSize);
        mStringPaint.setColor(Color.BLACK);
        mStringPaint.setTextAlign(Paint.Align.CENTER);


        Resources resources = getResources();
        SimpleListenerSetup setupAction = (id, functionality, tooltipStringResource) -> {
            String jsName = mWebView.getJsFunctionName(functionality);
            if (jsName == null) {
                Timber.d("Skipping functionality: %s", functionality);
            }
            View view = findViewById(id);
            view.setOnClickListener(v -> mWebView.execFunction(jsName));
            setTooltip(view, resources.getString(tooltipStringResource));
        };

        setupAction.apply(R.id.editor_button_bold, BOLD, R.string.visual_editor_tooltip_bold);
        setupAction.apply(R.id.editor_button_italic, ITALIC, R.string.visual_editor_tooltip_italic);
        setupAction.apply(R.id.editor_button_underline, UNDERLINE, R.string.visual_editor_tooltip_underline);
        setupAction.apply(R.id.editor_button_clear_formatting, CLEAR_FORMATTING, R.string.visual_editor_tooltip_clear_formatting);
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
        setupAndroidListener.apply(R.id.editor_button_customs, this::displayAddToolbarDialog, R.string.visual_editor_custom_buttons);
        setupAndroidListener.apply(R.id.editor_button_white_board, this::openWhiteBoard, R.string.visual_editor_white_board); // opens drawingActivity
        setupAndroidListener.apply(R.id.editor_button_cloze, this::performCloze, R.string.visual_editor_tooltip_cloze);
        setupAndroidListener.apply(R.id.editor_button_insert_mathjax, this::insertMathJax, R.string.visual_editor_tooltip_mathjax);
        setupAndroidListener.apply(R.id.editor_button_add_image, this::openAdvancedViewerForAddImage, R.string.visual_editor_tooltip_add_image);
        setupAndroidListener.apply(R.id.editor_button_record_audio, this::openAdvancedViewerForRecordAudio, R.string.visual_editor_tooltip_record_audio);
        setupAndroidListener.apply(R.id.editor_button_text_color, () -> this.openColorPicker(COLOR_PICKER_FOREGROUND, mForeGroundSelectedColor), R.string.visual_editor_tooltip_text_color);
        setupAndroidListener.apply(R.id.editor_button_background_color, () -> this.openColorPicker(COLOR_PICKER_BACKGROUND, mBackGroundSelectedColor), R.string.visual_editor_tooltip_background_color);


        updateCustomButtons();
    }

    private void openAdvancedViewerForRecordAudio() {
        if (!checkCollectionHasLoaded(R.string.visual_editor_could_not_start_add_audio)) {
            return;
        }
        IField field = new AudioRecordingField();
        openMultimediaEditor(field);
    }


    private void openAdvancedViewerForAddImage() {
        if (!checkCollectionHasLoaded(R.string.visual_editor_could_not_start_add_image)) {
            return;
        }

        IField field = new ImageField();
        openMultimediaEditor(field);
    }

    private void openMultimediaEditor(IField field) {
        IMultimediaEditableNote note = getCurrentMultimediaEditableNote(mVisualEditorActivity.getCol());
        if (note != null) {
            note.setField(0, field);
            Intent editCard = new Intent(mVisualEditorActivity, MultimediaEditFieldActivity.class);
            editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD_INDEX, 0);
            editCard.putExtra(MultimediaEditFieldActivity.EXTRA_FIELD, field);
            editCard.putExtra(MultimediaEditFieldActivity.EXTRA_WHOLE_NOTE, note);
            mVisualEditorActivity.startActivityForResultWithoutAnimation(editCard, REQUEST_MULTIMEDIA_EDIT);
        }
    }


    private MultimediaEditableNote getCurrentMultimediaEditableNote(Collection col) {
        MultimediaEditableNote note = NoteService.createEmptyNote(Objects.requireNonNull(col.getModels().get(mVisualEditorActivity.getModelId())));

        String[] fields = mVisualEditorActivity.getFields();
        NoteService.updateMultimediaNoteFromFields(col, fields, mVisualEditorActivity.getModelId(), note);
        return note;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean checkCollectionHasLoaded(@StringRes int resId) {
        if (mVisualEditorActivity.getModelId() == 0 || !mVisualEditorActivity.hasLoadedCol()) {
            //Haven't loaded yet.
            UIUtils.showThemedToast(mVisualEditorActivity, mVisualEditorActivity.getString(resId), false);
            return false;
        }
        return true;
    }



    private void insertMathJax() {
        mWebView.pasteHtml(MathJaxUtils.getMathJaxInsertionString());
    }


    private void setTooltip(View view, String tooltip) {
        TooltipCompat.setTooltipText(view, tooltip);
    }


    private void openColorPicker(int dialogId, Integer defaultColor) {
        // colors used in presets
        ColorPickerDialog colorPickerDialog = new ColorPickerDialog(mVisualEditorActivity);   // Pass the context.
        colorPickerDialog.setColors()
                .setColumns(5)                        		// Default number of columns is 5.
                .setDefaultSelectedColor(defaultColor)		// By default no color is used.
                .setColorItemShape(ColorItemShape.SQUARE)
                .setOnSelectColorListener(new OnSelectColorListener() {
                    @Override
                    public void onColorSelected(int color, int position) {
                        if (dialogId == COLOR_PICKER_FOREGROUND) {
                            mForeGroundSelectedColor = color;
                            mWebView.setSelectedTextColor(color);
                        } else if (dialogId == COLOR_PICKER_BACKGROUND) {
                            mBackGroundSelectedColor = color;
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
        List<String> fields = Arrays.asList(mVisualEditorActivity.getFields());
        fields.set(mVisualEditorActivity.getIndex(), mVisualEditorActivity.getCurrentText());
        return Note.ClozeUtils.getNextClozeIndex(fields);
    }

    private void openWhiteBoard() {
        mVisualEditorActivity.startActivityForResultWithoutAnimation(new Intent(mVisualEditorActivity, DrawingActivity.class), REQUEST_WHITE_BOARD_EDIT);
    }

    private void updateCustomButtons() {

        ArrayList<CustomToolbarButton> buttons = getToolbarButtons();

        clearCustomItems();

        for (CustomToolbarButton b : buttons) {

            // 0th button shows as '1' and is Ctrl + 1
            int visualIndex = b.getIndex() + 1;
            String text = Integer.toString(visualIndex);
            Drawable bmp = createDrawableForString(text);

            AppCompatImageButton v = new AppCompatImageButton(mVisualEditorActivity);
            v.setBackgroundDrawable(bmp);
            mCustomButtons.add(v);
            addView(v);

            // Allow Ctrl + 1...Ctrl + 0 for item 10.
            v.setTag(Integer.toString(visualIndex % 10));

            v.setOnClickListener(view -> mWebView.insertCustomTag(b.getPrefix(), b.getSuffix()));

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
        AppCompatImageButton button = new AppCompatImageButton(mVisualEditorActivity);
        button.setId(id);
        button.setBackgroundDrawable(drawable);

        // apply style
        int marginEnd = (int) Math.ceil(8 / getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMarginEnd(marginEnd);
        button.setLayoutParams(params);


        int fourDp = (int) Math.ceil(4 / getResources().getDisplayMetrics().density);

        button.setPadding(fourDp, fourDp, fourDp, fourDp);
        // end apply style


        this.addView(button, getChildCount());
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

    @SuppressWarnings("deprecation")
    protected int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mVisualEditorActivity.getWindowManager()
                .getDefaultDisplay()
                .getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    private int getVisibleItemCount() {
        int count = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() == View.VISIBLE){
                count++;
            }
        }
        return count;
    }

    public void clearCustomItems() {
        for (View v : mCustomButtons) {
            removeView(v);
        }
        mCustomButtons.clear();
    }


    private void displayAddToolbarDialog() {
        new MaterialDialog.Builder(mVisualEditorActivity)
                .title(R.string.add_toolbar_item)
                .customView(R.layout.note_editor_toolbar_add_custom_item, true)
                .positiveText(R.string.dialog_positive_create)
                .neutralText(R.string.help)
                .negativeText(R.string.dialog_cancel)
                .onNeutral((m, v) -> mVisualEditorActivity.openUrl(Uri.parse(mVisualEditorActivity.getString(R.string.link_manual_note_format_toolbar))))
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
        new MaterialDialog.Builder(mVisualEditorActivity)
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
        AnkiDroidApp.getSharedPrefs(mVisualEditorActivity).edit()
                .putStringSet("note_editor_custom_buttons", CustomToolbarButton.toStringSet(buttons))
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
        Set<String> set = AnkiDroidApp.getSharedPrefs(mVisualEditorActivity).getStringSet("note_editor_custom_buttons", new HashSet<>(0));
        return CustomToolbarButton.fromStringSet(set);
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