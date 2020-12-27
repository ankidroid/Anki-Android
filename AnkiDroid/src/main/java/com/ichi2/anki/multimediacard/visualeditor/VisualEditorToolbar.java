package com.ichi2.anki.multimediacard.visualeditor;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.LinearLayoutCompat;

// currently only used for adding new view to linearLayout of VisualEditorToolbar
public class VisualEditorToolbar extends LinearLayoutCompat {

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

}