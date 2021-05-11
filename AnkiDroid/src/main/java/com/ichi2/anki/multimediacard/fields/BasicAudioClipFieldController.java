/****************************************************************************************
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.multimediacard.fields;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;
import com.ichi2.compat.CompatHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.ui.FixedTextView;

import java.io.File;
import java.io.InputStream;

import timber.log.Timber;

public class BasicAudioClipFieldController extends FieldControllerBase implements IFieldController {

    private static final int ACTIVITY_SELECT_AUDIO_CLIP = 1;

    private File mStoringDirectory;

    private TextView mTvAudioClip;


    @Override
    public void createUI(Context context, LinearLayout layout) {

        Collection col = CollectionHelper.getInstance().getCol(context);
        mStoringDirectory = new File(col.getMedia().dir());

        Button mBtnLibrary = new Button(mActivity);
        mBtnLibrary.setText(mActivity.getText(R.string.multimedia_editor_image_field_editing_library));
        mBtnLibrary.setOnClickListener(v -> {
            Intent i = new Intent();
            i.setType("audio/*");
            i.setAction(Intent.ACTION_GET_CONTENT);
            // Only get openable files, to avoid virtual files issues with Android 7+
            i.addCategory(Intent.CATEGORY_OPENABLE);
            String chooserPrompt = mActivity.getResources().getString(R.string.multimedia_editor_popup_audio_clip);
            mActivity.startActivityForResultWithoutAnimation(Intent.createChooser(i, chooserPrompt), ACTIVITY_SELECT_AUDIO_CLIP);
        });

        layout.addView(mBtnLibrary, ViewGroup.LayoutParams.MATCH_PARENT);

        mTvAudioClip = new FixedTextView(mActivity);
        if (mField.getAudioPath() == null) {
            mTvAudioClip.setVisibility(View.GONE);
        } else {
            mTvAudioClip.setText(mField.getAudioPath());
            mTvAudioClip.setVisibility(View.VISIBLE);
        }

        layout.addView(mTvAudioClip, ViewGroup.LayoutParams.MATCH_PARENT);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((resultCode != Activity.RESULT_CANCELED) && (requestCode == ACTIVITY_SELECT_AUDIO_CLIP)) {
            try {
                handleAudioSelection(data);
            } catch (Exception e) {
                AnkiDroidApp.sendExceptionReport(e, "handleAudioSelection:unhandled");
                UIUtils.showThemedToast(AnkiDroidApp.getInstance().getApplicationContext(),
                        AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true);
            }
        }
    }


    private void handleAudioSelection(Intent data) {
        Uri selectedClip = data.getData();

        // Get information about the selected document
        String[] queryColumns = { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE };
        String[] audioClipFullNameParts;
        try (Cursor cursor = mActivity.getContentResolver().query(selectedClip, queryColumns, null, null, null)) {
            if (cursor == null) {
                UIUtils.showThemedToast(AnkiDroidApp.getInstance().getApplicationContext(),
                        AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true);
                return;
            }

            cursor.moveToFirst();
            String audioClipFullName = cursor.getString(0);
            audioClipFullNameParts = audioClipFullName.split("\\.");
            if (audioClipFullNameParts.length < 2) {
                try {
                    Timber.i("Audio clip name does not have extension, using second half of mime type");
                    audioClipFullNameParts = new String[] {audioClipFullName, cursor.getString(2).split("/")[1]};
                } catch (Exception e) {
                    Timber.w(e);
                    // This code is difficult to stabilize - it is not clear how to handle files with no extension
                    // and apparently we may fail to get MIME_TYPE information - in that case we will gather information
                    // about what people are experiencing in the real world and decide later, but without crashing at least
                    AnkiDroidApp.sendExceptionReport(e, "Audio Clip addition failed. Name " + audioClipFullName + " / cursor mime type column type " + cursor.getType(2));
                    UIUtils.showThemedToast(AnkiDroidApp.getInstance().getApplicationContext(),
                            AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true);
                    return;
                }
            }
        }

        // We may receive documents we can't access directly, we have to copy to a temp file
        File clipCopy;
        try {
            clipCopy = File.createTempFile("ankidroid_audioclip_" + audioClipFullNameParts[0],
                    "." + audioClipFullNameParts[1],
                    mStoringDirectory);
            Timber.d("audio clip picker file path is: %s", clipCopy.getAbsolutePath());
        } catch (Exception e) {
            Timber.e(e, "Could not create temporary audio file. ");
            AnkiDroidApp.sendExceptionReport(e, "handleAudioSelection:tempFile");
            UIUtils.showThemedToast(AnkiDroidApp.getInstance().getApplicationContext(),
                    AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true);
            return;
        }

        // Copy file contents into new temp file. Possibly check file size first and warn if large?
        try (InputStream inputStream = mActivity.getContentResolver().openInputStream(selectedClip)) {
            CompatHelper.getCompat().copyFile(inputStream, clipCopy.getAbsolutePath());

            // If everything worked, hand off the information
            mField.setHasTemporaryMedia(true);
            mField.setAudioPath(clipCopy.getAbsolutePath());
            mTvAudioClip.setText(mField.getFormattedValue());
            mTvAudioClip.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Timber.e(e, "Unable to copy audio file from ContentProvider");
            AnkiDroidApp.sendExceptionReport(e, "handleAudioSelection:copyFromProvider");
            UIUtils.showThemedToast(AnkiDroidApp.getInstance().getApplicationContext(),
                    AnkiDroidApp.getInstance().getString(R.string.multimedia_editor_something_wrong), true);
        }
    }


    @Override
    public void onDone() { /* nothing */ }

    @Override
    public void onFocusLost() { /* nothing */ }

    @Override
    public void onDestroy() { /* nothing */ }
}
