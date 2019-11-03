package com.ichi2.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class IntentTop extends Intent {
    public IntentTop() {
        super();
        defaultFlags();
    }
    public IntentTop(Intent o) {
        super(o);
        defaultFlags();
    }
    public IntentTop(String action) {
        super(action);
        defaultFlags();
    }
    public IntentTop(String action, Uri uri) {
        super(action, uri);
        defaultFlags();
    }
    public IntentTop(Context packageContext, Class<?> cls) {
        super(packageContext, cls);
        defaultFlags();
    }
    public IntentTop(String action, Uri uri, Context packageContext, Class<?> cls) {
        super(action, uri, packageContext, cls);
        defaultFlags();
    }

    protected void defaultFlags() {
        addFlags(FLAG_ACTIVITY_SINGLE_TOP);
        addFlags(FLAG_ACTIVITY_CLEAR_TOP);
    }
}
