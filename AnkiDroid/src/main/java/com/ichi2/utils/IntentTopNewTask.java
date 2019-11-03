package com.ichi2.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class IntentTopNewTask extends IntentTop {
    public IntentTopNewTask() {
        super();
        defaultFlags();
    }
    public IntentTopNewTask(Intent o) {
        super(o);
        defaultFlags();
    }
    public IntentTopNewTask(String action) {
        super(action);
        defaultFlags();
    }
    public IntentTopNewTask(String action, Uri uri) {
        super(action, uri);
        defaultFlags();
    }
    public IntentTopNewTask(Context packageContext, Class<?> cls) {
        super(packageContext, cls);
        defaultFlags();
    }
    public IntentTopNewTask(String action, Uri uri, Context packageContext, Class<?> cls) {
        super(action, uri, packageContext, cls);
        defaultFlags();
    }


    protected void defaultFlags() {
        super.defaultFlags();
        addFlags(FLAG_ACTIVITY_NEW_TASK);
    }
}
