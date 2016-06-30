
package com.ichi2.anki;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.TextView;

import com.ichi2.themes.Themes;


public class FieldEditText extends EditText {

    public static final String NEW_LINE = System.getProperty("line.separator");
    public static final String NL_MARK = "newLineMark";

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


    public FieldEditText(Context context, int ord, String name, String content) {
        super(context);
        init(ord, name, content);
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


    public void init(int ord, String name, String content) {
        mOrd = ord;
        mName = name;

        if (content == null) {
            content = "";
        } else {
            content = content.replaceAll("<br(\\s*\\/*)>", NEW_LINE);
        }
        setText(content);
        setContentDescription(name);
        setMinimumWidth(400);
        mOrigBackground = getBackground();
        // Fixes bug where new instances of this object have wrong colors, probably
        // from some reuse mechanic in Android.
        setDefaultStyle();
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
