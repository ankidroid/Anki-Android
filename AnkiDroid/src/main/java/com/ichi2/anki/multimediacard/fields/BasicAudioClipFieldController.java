package com.ichi2.anki.multimediacard.fields;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.libanki.Collection;

import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.ExifUtil;

import java.io.File;
import java.io.IOException;

import timber.log.Timber;

public class BasicAudioClipFieldController extends FieldControllerBase implements IFieldController {

    protected static final int ACTIVITY_SELECT_AUDIO_CLIP = 1;

    protected Button mBtnLibrary;
    protected TextView mTvAudioClip;


    @Override
    public void createUI(Context context, LinearLayout layout) {
        mBtnLibrary = new Button(mActivity);
        mBtnLibrary.setText(gtxt(R.string.multimedia_editor_image_field_editing_library));
        mBtnLibrary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                mActivity.startActivityForResult(i, ACTIVITY_SELECT_AUDIO_CLIP);
            }
        });

        layout.addView(mBtnLibrary, android.view.ViewGroup.LayoutParams.FILL_PARENT);

        mTvAudioClip = new TextView(mActivity);
        if (mField.getAudioPath() == null) {
            mTvAudioClip.setVisibility(View.GONE);
        }
        else {
            mTvAudioClip.setText(mField.getAudioPath());
            mTvAudioClip.setVisibility(View.VISIBLE);
        }

        layout.addView(mTvAudioClip, android.view.ViewGroup.LayoutParams.FILL_PARENT);
    }

    private String gtxt(int id) {
        return mActivity.getText(id).toString();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            // Do Nothing.
        } else if (requestCode == ACTIVITY_SELECT_AUDIO_CLIP) {

            Uri selectedClip = data.getData();
            // Timber.d(selectedImage.toString());
            String[] filePathColumn = { MediaStore.MediaColumns.DATA };

            Cursor cursor = mActivity.getContentResolver().query(selectedClip, filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String filePath = cursor.getString(columnIndex);
            cursor.close();

            Timber.d(filePath);
            mField.setAudioPath(filePath);

            mTvAudioClip.setText(mField.getFormattedValue());
            mTvAudioClip.setVisibility(View.VISIBLE);
        }
        //setPreviewImage(mField.getImagePath(), getMaxImageSize());
    }

    @Override
    public void onDone() {

    }

    @Override
    public void onDestroy() {

    }
}
