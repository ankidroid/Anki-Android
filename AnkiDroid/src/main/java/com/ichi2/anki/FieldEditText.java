
package com.ichi2.anki;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.LocaleList;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import timber.log.Timber;

import com.ichi2.themes.Themes;
import com.ichi2.ui.FixedEditText;
import com.ichi2.ui.FixedTextView;

import java.util.Objects;

import static android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;


public class FieldEditText extends FixedEditText {

    @NonNull
    public static final String NEW_LINE = Objects.requireNonNull(System.getProperty("line.separator"));

    private String mName;
    private int mOrd;
    private Drawable mOrigBackground;
    @Nullable
    private TextSelectionListener mSelectionChangeListener;


    public FieldEditText(Context context) {
        super(context);
    }


    public FieldEditText(Context context, AttributeSet attr) {
        super(context, attr);
    }


    public FieldEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    public Parcelable onSaveInstanceState() {
        // content text has been saved in NoteEditor.java, restore twice caused issue#5660
        super.onSaveInstanceState();
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (shouldDisableExtendedTextUi()) {
            Timber.i("Disabling Extended Text UI");
            this.setImeOptions(this.getImeOptions() | IME_FLAG_NO_EXTRACT_UI);
        }
    }

    private boolean shouldDisableExtendedTextUi() {
        try {
            SharedPreferences sp = AnkiDroidApp.getSharedPrefs(this.getContext());
            return sp.getBoolean("disableExtendedTextUi", false);
        } catch (Exception e) {
            Timber.e(e, "Failed to get extended UI preference");
            return false;
        }
    }


    public int getOrd() {
        return mOrd;
    }


    public String getName() {
        return mName;
    }


    public void init() {
        setMinimumWidth(400);
        mOrigBackground = getBackground();
        // Fixes bug where new instances of this object have wrong colors, probably
        // from some reuse mechanic in Android.
        setDefaultStyle();
    }


    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (mSelectionChangeListener != null) {
            try {
                mSelectionChangeListener.onSelectionChanged(selStart, selEnd);
            } catch (Exception e) {
                Timber.w(e, "mSelectionChangeListener");
            }
        }
        super.onSelectionChanged(selStart, selEnd);
    }



    @RequiresApi(api = Build.VERSION_CODES.N)
    public void setHintLocale(@NonNull Locale locale) {
        Timber.d("Setting hint locale of '%s' to '%s'", mName, locale);
        setImeHintLocales(new LocaleList(locale));
    }

    /**
     * Modify the style of this view to represent a duplicate field.
     */
    public void setDupeStyle() {
        setBackgroundColor(Themes.getColorFromAttr(getContext(), R.attr.duplicateColor));
    }


    /**
     * Restore the default style of this view.
     */
    public void setDefaultStyle() {
        setBackgroundDrawable(mOrigBackground);
    }

    public void setSelectionChangeListener(TextSelectionListener listener) {
        this.mSelectionChangeListener = listener;
    }


    public void setContent(String content) {
        if (content == null) {
            content = "";
        } else {
            content = content.replaceAll("<br(\\s*/*)>", NEW_LINE);
        }
        setText(content);
    }


    public void setOrd(int ord) {
        mOrd = ord;
    }


    public interface TextSelectionListener {
        void onSelectionChanged(int selStart, int selEnd);
    }
}
