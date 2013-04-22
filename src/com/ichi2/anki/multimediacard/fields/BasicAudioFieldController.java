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

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.R.drawable;
import com.ichi2.anki.multimediacard.AudioView;
import com.ichi2.utils.DiskUtil;

import java.io.File;
import java.io.IOException;

public class BasicAudioFieldController extends FieldControllerBase implements IFieldController {

    protected static final int ACTIVITY_RECORD_AUDIO = 1;

    /**
     * This controller always return a temporary path where it writes the audio
     */
    private String tempAudioPath;
    private String origAudioPath;


    @Override
    public void createUI(LinearLayout layout) {
        origAudioPath = mField.getAudioPath();

        boolean bExist = false;

        if (origAudioPath != null) {
            File f = new File(origAudioPath);

            if (f.exists()) {
                tempAudioPath = f.getAbsolutePath();
                bExist = true;
            }
        }

        if (!bExist) {
            File file = null;
            try {
                file = File.createTempFile("ankidroid_audiorec", ".3gp", DiskUtil.getStoringDirectory());
                tempAudioPath = file.getAbsolutePath();
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, "Could not create temporary audio file. " + e.getMessage());
                tempAudioPath = null;
            }
        }

        AudioView audioView = AudioView.createRecorderInstance(mActivity, R.drawable.av_play, R.drawable.av_pause,
                R.drawable.av_stop, R.drawable.av_rec, R.drawable.av_rec_stop, tempAudioPath);

        audioView.setOnRecordingFinishEventListener(new AudioView.OnRecordingFinishEventListener() {
            @Override
            public void onRecordingFinish(View v) {
                // currentFilePath.setText("Recording done, you can preview it. Hit save after finish");
                mField.setAudioPath(tempAudioPath);
                mField.setHasTemporaryMedia(true);
            }
        });
        layout.addView(audioView, LinearLayout.LayoutParams.FILL_PARENT);
    }


    @Override
    public void onDone() {
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }


    @Override
    public void onDestroy() {
    }

}
