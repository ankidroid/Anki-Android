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

package com.ichi2.anki.noteeditor;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.libanki.Utils;
import com.ichi2.utils.ViewGroupUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import kotlin.Suppress;
import timber.log.Timber;

public class Toolbar extends FrameLayout {

    private TextFormatListener mFormatCallback;
    private LinearLayout mToolbar;
    private LinearLayout mToolbarLayout;
    private List<View> mCustomButtons = new ArrayList<>();
    private List<LinearLayout> mRows = new ArrayList<>();
    private View mClozeIcon;

    private Paint mStringPaint;


    public Toolbar(@NonNull Context context) {
        super(context);
        init();
    }


    public Toolbar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    public Toolbar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    public Toolbar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.note_editor_toolbar, this, true);

        int paintSize = dpToPixels(24);

        mStringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStringPaint.setTextSize(paintSize);
        mStringPaint.setColor(Color.BLACK);
        mStringPaint.setTextAlign(Paint.Align.CENTER);

        this.mToolbar = findViewById(R.id.editor_toolbar_internal);
        this.mToolbarLayout = findViewById(R.id.toolbar_layout);
        setClick(R.id.note_editor_toolbar_button_bold, "<b>", "</b>");
        setClick(R.id.note_editor_toolbar_button_italic, "<em>", "</em>");
        setClick(R.id.note_editor_toolbar_button_underline, "<u>", "</u>");

        setClick(R.id.note_editor_toolbar_button_insert_mathjax, "\\(", "\\)");
        setClick(R.id.note_editor_toolbar_button_horizontal_rule, "<hr>", "");
        findViewById(R.id.note_editor_toolbar_button_font_size).setOnClickListener(l -> displayFontSizeDialog());
        findViewById(R.id.note_editor_toolbar_button_title).setOnClickListener(l -> displayInsertHeadingDialog());
        this.mClozeIcon = findViewById(R.id.note_editor_toolbar_button_cloze);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // hack to see if only CTRL is pressed - might not be perfect.
        // I'll avoid checking "function" here as it may be required to press Ctrl
        if (!event.isCtrlPressed() || event.isAltPressed() || event.isShiftPressed() || event.isMetaPressed()) {
            return false;
        }

        char c;
        try {
            c = (char) event.getUnicodeChar(0);
        } catch (Exception e) {
            Timber.w(e);
            return false;
        }

        if (c == '\0') {
            return false;
        }

        String expected = Character.toString(c);
        for (View v : ViewGroupUtils.getAllChildrenRecursive(this)) {
            if (Utils.equals(expected, v.getTag())) {
                Timber.i("Handling Ctrl + %s", c);
                v.performClick();
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }


    private int dpToPixels(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }


    public View getClozeIcon() {
        // HACK until API 21 FIXME can this be altered now?
        return mClozeIcon;
    }


    @NonNull
    public AppCompatImageButton insertItem(@IdRes int id, @DrawableRes int drawable, Runnable runnable) {
        // we use the light theme here to ensure the tint is black on both
        // A null theme can be passed after colorControlNormal is defined (API 25)
        Context themeContext = new ContextThemeWrapper(getContext(), R.style.Theme_Light_Compat);
        VectorDrawableCompat d = VectorDrawableCompat.create(getContext().getResources(), drawable, themeContext.getTheme());
        return insertItem(id, d, runnable);
    }

    @NonNull
    public View insertItem(int id, Drawable drawable, TextFormatter formatter) {
        return insertItem(id, drawable, () -> onFormat(formatter));
    }

    @NonNull
    public AppCompatImageButton insertItem(@IdRes int id, Drawable drawable, Runnable runnable) {
        Context context = getContext();
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
        int margin = dpToPixels(8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(margin, margin/2, margin, margin/2);
        button.setLayoutParams(params);


        int twoDp = (int) Math.ceil(2 / context.getResources().getDisplayMetrics().density);

        button.setPadding(twoDp, twoDp, twoDp, twoDp);
        // end apply style

        if (shouldScrollToolbar()) {
            this.mToolbar.addView(button, mToolbar.getChildCount());
        } else {
            addViewToToolbar(button);
        }

        mCustomButtons.add(button);
        button.setOnClickListener(l -> runnable.run());

        // Hack - items are truncated from the scrollview
        View v = findViewById(R.id.toolbar_layout);

        int expectedWidth = getVisibleItemCount(mToolbar) * dpToPixels(48);
        int width = getScreenWidth();
        LayoutParams p = new LayoutParams(v.getLayoutParams());
        p.gravity = Gravity.CENTER_VERTICAL | ((expectedWidth > width) ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        v.setLayoutParams(p);

        return button;
    }


    @SuppressWarnings("deprecation") // #9333: getDefaultDisplay & getMetrics
    protected int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager()
                .getDefaultDisplay()
                .getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }


    public void clearCustomItems() {
        for (View v : mCustomButtons) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        mCustomButtons.clear();
    }

    public void setFormatListener(TextFormatListener formatter) {
        mFormatCallback = formatter;
    }

    private void displayFontSizeDialog() {
        String[] results = getResources().getStringArray(R.array.html_size_codes);

        // Might be better to add this as a fragment - let's see.
        new MaterialDialog.Builder(getContext())
                .items(R.array.html_size_code_labels)
                .itemsCallback((dialog, view, pos, string) -> {
                    String prefix = "<span style=\"font-size:" + results[pos] + "\">";
                    String suffix = "</span>";
                    TextWrapper formatter = new TextWrapper(prefix, suffix);
                    onFormat(formatter);
                })
                .title(R.string.menu_font_size)
                .show();
    }


    private void displayInsertHeadingDialog() {
        new MaterialDialog.Builder(getContext())
                .items(new String[] { "h1", "h2", "h3", "h4", "h5" })
                .itemsCallback((dialog, view, pos, string) -> {
                    String prefix = "<" + string + ">";
                    String suffix = "</" + string +">";
                    TextWrapper formatter = new TextWrapper(prefix, suffix);
                    onFormat(formatter);
                })
                .title(R.string.insert_heading)
                .show();
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


    private int getVisibleItemCount(LinearLayout layout) {
        int count = 0;
        for (int i = 0; i < layout.getChildCount(); i++) {
            if (layout.getChildAt(i).getVisibility() == View.VISIBLE) {
                count++;
            }
        }
        return count;
    }


    private void addViewToToolbar(AppCompatImageButton button) {
        int expectedWidth = getVisibleItemCount(mToolbar) * dpToPixels(48);
        int width = getScreenWidth();
        if (expectedWidth <= width) {
            this.mToolbar.addView(button, mToolbar.getChildCount());
            return;
        }
        boolean spaceLeft = false;
        if (!mRows.isEmpty()) {
            LinearLayout row = mRows.get(mRows.size() - 1);
            int expectedRowWidth = getVisibleItemCount(row) * dpToPixels(48);
            if (expectedRowWidth <= width) {
                row.addView(button, row.getChildCount());
                spaceLeft = true;
            }
        }
        if (!spaceLeft) {
            LinearLayout row = new LinearLayout(getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(params);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(button);
            mRows.add(row);
            mToolbarLayout.addView(mRows.get(mRows.size() - 1));
        }
    }

    private void setClick(@IdRes int id, String prefix, String suffix) {
        setClick(id, new TextWrapper(prefix, suffix));
    }


    private void setClick(int id, TextFormatter textWrapper) {
        findViewById(id).setOnClickListener(l -> onFormat(textWrapper));
    }


    public void onFormat(TextFormatter formatter) {
        if (mFormatCallback == null) {
            return;
        }

        mFormatCallback.performFormat(formatter);
    }

    public void setIconColor(@ColorInt int color) {
        for (int i = 0; i < this.mToolbar.getChildCount(); i++) {
            AppCompatImageButton button = (AppCompatImageButton) this.mToolbar.getChildAt(i);
            button.setColorFilter(color);
        }
        mStringPaint.setColor(color);
    }

    protected static boolean shouldScrollToolbar() {
        return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).getBoolean("noteEditorScrollToolbar", true);
    }

    public interface TextFormatListener {
        void performFormat(TextFormatter formatter);
    }

    public interface TextFormatter {
        TextWrapper.StringFormat format(String s);
    }

    public static class TextWrapper implements TextFormatter {
        private final String mPrefix;
        private final String mSuffix;

        public TextWrapper(String prefix, String suffix) {
            this.mPrefix = prefix;
            this.mSuffix = suffix;
        }


        @Override
        public StringFormat format(String s) {
            StringFormat stringFormat = new StringFormat();
            stringFormat.result = mPrefix + s + mSuffix;
            if (s.length() == 0) {
                stringFormat.start = mPrefix.length();
                stringFormat.end = mPrefix.length();
            } else {
                stringFormat.start = 0;
                stringFormat.end = stringFormat.result.length();
            }

            return stringFormat;
        }

        public static class StringFormat {
            public String result;
            public int start;
            public int end;
        }
    }

}
