/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

import android.content.Context;
import android.content.Intent;

import android.widget.LinearLayout;

import com.ichi2.anki.R;
import com.ichi2.anki.multimediacard.AudioView;

import java.io.File;

public class BasicAudioRecordingFieldController extends FieldControllerBase implements IFieldController {

    /**
     * This controller always return a temporary path where it writes the audio
     */
    private String mTempAudioPath;
    private AudioView mAudioView;


    @Override
    public void createUI(Context context, LinearLayout layout) {
        String origAudioPath = mField.getAudioPath();

        boolean bExist = false;

        if (origAudioPath != null) {
            File f = new File(origAudioPath);

            if (f.exists()) {
                mTempAudioPath = f.getAbsolutePath();
                bExist = true;
            }
        }

        if (!bExist) {
            mTempAudioPath = AudioView.generateTempAudioFile(mActivity);
        }

        mAudioView = AudioView.createRecorderInstance(mActivity, R.drawable.av_play, R.drawable.av_pause,
                R.drawable.av_stop, R.drawable.av_rec, R.drawable.av_rec_stop, mTempAudioPath);
        mAudioView.setOnRecordingFinishEventListener(v -> {
            // currentFilePath.setText("Recording done, you can preview it. Hit save after finish");
            // FIXME is this okay if it is still null?
            mField.setAudioPath(mTempAudioPath);
            mField.setHasTemporaryMedia(true);
        });
        layout.addView(mAudioView, LinearLayout.LayoutParams.MATCH_PARENT);
    }


    @Override
    public void onDone() {
        mAudioView.notifyStopRecord();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // do nothing
    }

    @Override
    public void onFocusLost() {
        mAudioView.notifyReleaseRecorder();
    }


    @Override
    public void onDestroy() {
        mAudioView.notifyReleaseRecorder();
    }
}
