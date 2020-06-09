
package com.ichi2.anki;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.LocaleList;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import androidx.appcompat.widget.AppCompatEditText;
import timber.log.Timber;

import com.ichi2.themes.Themes;

import java.util.Objects;

import static android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;


public class FieldEditText extends AppCompatEditText {

    @NonNull
    public static final String NEW_LINE = Objects.requireNonNull(System.getProperty("line.separator"));

    private String mName;
    private int mOrd;
    private Drawable mOrigBackground;


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
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.getContext());
            return sp.getBoolean("disableExtendedTextUi", false);
        } catch (Exception e) {
            Timber.e(e, "Failed to get extended UI preference");
            return false;
        }
    }


    public TextView getLabel() {
        TextView label = new TextView(this.getContext());
        label.setText(mName);
        return label;
    }


    public int getOrd() {
        return mOrd;
    }


    public String getName() {
        return mName;
    }


    public void init(int ord, String name, String content, @Nullable Locale hintLocale) {
        mOrd = ord;
        mName = name;

        if (content == null) {
            content = "";
        } else {
            content = content.replaceAll("<br(\\s*\\/*)>", NEW_LINE);
        }
        setText(content);
        setContentDescription(name);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hintLocale != null) {
            setHintLocale(hintLocale);
        }
        setMinimumWidth(400);
        mOrigBackground = getBackground();
        // Fixes bug where new instances of this object have wrong colors, probably
        // from some reuse mechanic in Android.
        setDefaultStyle();
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
}
