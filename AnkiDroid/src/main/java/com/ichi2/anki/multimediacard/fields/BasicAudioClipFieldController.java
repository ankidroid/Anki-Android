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

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import timber.log.Timber;

public class BasicAudioClipFieldController extends FieldControllerBase implements IFieldController {

    private static final int ACTIVITY_SELECT_AUDIO_CLIP = 1;

    private File storingDirectory;

    private TextView mTvAudioClip;


    @Override
    public void createUI(Context context, LinearLayout layout) {

        Collection col = CollectionHelper.getInstance().getCol(context);
        storingDirectory = new File(col.getMedia().dir());

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

        mTvAudioClip = new TextView(mActivity);
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

            Uri selectedClip = data.getData();
            Timber.d(selectedClip.toString());

            // Get information about the selected document
            String[] queryColumns = { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE };
            Cursor cursor = mActivity.getContentResolver().query(selectedClip, queryColumns, null, null, null);
            cursor.moveToFirst();
            String audioClipFullName = cursor.getString(0);
            // Note this could be unsafe, null name, or name without standard extension etc, go off mime types instead?
            String[] audioClipFullNameParts = audioClipFullName.split("\\.");
            cursor.close();

            // We may receive documents we can't access directly, we have to copy to a temp file
            File clipCopy = null;
            try {
                clipCopy = File.createTempFile("ankidroid_audioclip_" + audioClipFullNameParts[0],
                        "." + audioClipFullNameParts[1],
                        storingDirectory);
            } catch (IOException e) {
                Timber.e(e, "Could not create temporary audio file. ");
            }

            // Copy file contents into new temp file. Possibly check file size first and warn if large?
            InputStream inputStream = null;
            try {
                inputStream = mActivity.getContentResolver().openInputStream(selectedClip);

                // copy the picked file contents to internal media iteratively using Buffered Streams?
                OutputStream outputStream = null;
                try {
                    // FIXME no no no no
                    clipCopy.setWritable(true, false);
                    outputStream = new FileOutputStream(clipCopy);
                    byte buffer[] = new byte[1024];
                    int length = 0;

                    while((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer,0,length);
                    }
                }
                catch (IOException e) {
                    Timber.e(e, "error in creating a file");
                }
                finally {
                    try {
                        outputStream.close();
                    }
                    catch (IOException ioe) {
                        // nothing?
                    }
                }

                mField.setHasTemporaryMedia(true);
            }
            catch (FileNotFoundException fnfe) {
                Timber.e(fnfe, "Unable to open selected audio clip");
            }
            finally {
                // close the file descriptor here?
                try {
                    inputStream.close();
                }
                catch (IOException ioe) {
                    // nothing
                }
            }

            Timber.d("audio clip picker file path is: %s", clipCopy.getAbsolutePath());
            mField.setAudioPath(clipCopy.getAbsolutePath());

            mTvAudioClip.setText(mField.getFormattedValue());
            mTvAudioClip.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDone() { /* nothing */ }

    @Override
    public void onFocusLost() { /* nothing */ }

    @Override
    public void onDestroy() { /* nothing */ }
}
