package com.ichi2.anki;

import androidx.appcompat.widget.Toolbar;
import timber.log.Timber;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.FileNotFoundException;

/**
 * Drawer actvity will allow user to draw image on empty activity
 * user can use all basic whiteboard functionally and can save image from this activity.
 */
public class DrawingActivity extends AnkiActivity {

    public static final String EXTRA_RESULT_WHITEBOARD = "drawing.editedImage";
    private LinearLayout mColorPalette;
    private Whiteboard mWhiteboard;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            enableToolbar(toolbar);
        }

        mColorPalette = findViewById(R.id.whiteboard_editor);

        mWhiteboard = Whiteboard.instance(this, false);

        mWhiteboard.setOnTouchListener((v, event) -> mWhiteboard.handleTouchEvent(event));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.drawing_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.action_save:
                Timber.i("Drawing:: Save button pressed");
                finishWithSuccess();
                break;
            case R.id.action_whiteboard_edit:
                Timber.i("Drawing:: Pen Color button pressed");
                if (mColorPalette.getVisibility() == View.GONE) {
                    mColorPalette.setVisibility(View.VISIBLE);
                } else {
                    mColorPalette.setVisibility(View.GONE);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void finishWithSuccess() {
        try {
            String savedWhiteboardFileName = mWhiteboard.saveWhiteboard(getCol().getTime());
            Intent resultData = new Intent();
            resultData.putExtra(DrawingActivity.EXTRA_RESULT_WHITEBOARD, savedWhiteboardFileName);
            setResult(RESULT_OK, resultData);
        } catch (FileNotFoundException e) {
            Timber.w(e);
        } finally {
            finishActivityWithFade(this);
        }
    }

}