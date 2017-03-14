package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;

public class ClozeInserter extends Activity {
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, "{{c1::" + text + "}}");
        setResult(RESULT_OK, intent);
        finish();
    }
}
