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
package com.ichi2.anki.multimediacard.fields

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.ichi2.anki.R
import com.ichi2.anki.multimediacard.AudioView
import com.ichi2.anki.multimediacard.AudioView.Companion.createRecorderInstance
import com.ichi2.anki.multimediacard.AudioView.Companion.generateTempAudioFile
import com.ichi2.anki.multimediacard.AudioView.OnRecordingFinishEventListener
import com.ichi2.annotations.NeedsTest
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.UiUtil.makeBold
import java.io.File

/**
 * This controller always return a temporary path where it writes the audio
 */
class BasicAudioRecordingFieldController : FieldControllerBase(), IFieldController {

    private var mTempAudioPath: String? = null
    private var mAudioView: AudioView? = null

    @NeedsTest("'Field Contents' label should be invisible if there's no fields with contents")
    override fun createUI(context: Context, layout: LinearLayout) {
        val origAudioPath = mField.audioPath
        var bExist = false
        if (origAudioPath != null) {
            val f = File(origAudioPath)
            if (f.exists()) {
                mTempAudioPath = f.absolutePath
                bExist = true
            }
        }
        if (!bExist) {
            mTempAudioPath = generateTempAudioFile(mActivity)
        }

        mAudioView = createRecorderInstance(
            context = mActivity,
            resPlay = R.drawable.ic_play_arrow_white_24dp,
            resPause = R.drawable.ic_pause_white_24dp,
            resStop = R.drawable.ic_stop_white_24dp,
            resRecord = R.drawable.ic_rec,
            resRecordStop = R.drawable.ic_rec_stop,
            audioPath = mTempAudioPath!!
        )
        mAudioView!!.setOnRecordingFinishEventListener(object : OnRecordingFinishEventListener {
            override fun onRecordingFinish(v: View) {
                // currentFilePath.setText("Recording done, you can preview it. Hit save after finish");
                // FIXME is this okay if it is still null?
                mField.audioPath = mTempAudioPath
                mField.hasTemporaryMedia = true
            }
        })
        layout.addView(mAudioView, LinearLayout.LayoutParams.MATCH_PARENT)

        context.apply {
            // add preview of the field data to provide context to the user
            // use a separate scrollview to ensure that the content does not push the buttons off-screen when scrolled
            val sv = ScrollView(this)
            layout.addView(sv)
            val previewLayout = LinearLayout(this) // scrollView can only have one child
            previewLayout.orientation = LinearLayout.VERTICAL
            sv.addView(previewLayout)
            val label = FixedTextView(this)
            label.textSize = 20f
            label.text = makeBold(this.getString(R.string.audio_recording_field_list))
            label.gravity = Gravity.CENTER_HORIZONTAL
            previewLayout.addView(label)
            var hasTextContents = false
            for (i in 0 until mNote.initialFieldCount) {
                val field = mNote.getInitialField(i)
                val textView = FixedTextView(this)
                textView.text = field?.text
                textView.textSize = 16f
                textView.setPadding(16, 0, 16, 24)
                previewLayout.addView(textView)
                hasTextContents = hasTextContents or !field?.text.isNullOrBlank()
            }
            label.visibility = if (hasTextContents) View.VISIBLE else View.GONE
        }
    }

    override fun onDone() {
        mAudioView!!.notifyStopRecord()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // do nothing
    }

    override fun onFocusLost() {
        mAudioView!!.notifyReleaseRecorder()
    }

    override fun onDestroy() {
        mAudioView!!.notifyReleaseRecorder()
    }
}
