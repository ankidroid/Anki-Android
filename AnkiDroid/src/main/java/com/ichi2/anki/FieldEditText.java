
package com.ichi2.anki;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import timber.log.Timber;

import com.ichi2.themes.Themes;
import com.ichi2.ui.FixedEditText;

import java.util.Objects;

import static android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;


public class FieldEditText extends FixedEditText {

    @NonNull
    public static final String NEW_LINE = Objects.requireNonNull(System.getProperty("line.separator"));

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
        Timber.d("Setting hint locale to '%s'", locale);
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

    @Nullable
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable state = super.onSaveInstanceState();

        SavedState savedState = new SavedState(state);
        savedState.mOrd = mOrd;

        return savedState;
    }


    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mOrd = ss.mOrd;
    }

    static class SavedState extends BaseSavedState {
        private int mOrd;

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mOrd);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel source) {
                        return new SavedState(source);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

        private SavedState(Parcel in) {
            super(in);
            this.mOrd = in.readInt();
        }
    }

    public interface TextSelectionListener {
        void onSelectionChanged(int selStart, int selEnd);
    }
}
