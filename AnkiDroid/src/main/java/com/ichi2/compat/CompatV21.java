
package com.ichi2.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;

import com.ichi2.anki.AnkiDroidApp;

import java.util.HashMap;

import timber.log.Timber;

/** Implementation of {@link Compat} for SDK level 21 */
@TargetApi(21)
public class CompatV21 extends CompatV19 implements Compat {

    @Override
    public void setSelectableBackground(View view) {
        // Ripple effect
        int[] attrs = new int[] {android.R.attr.selectableItemBackground};
        TypedArray ta = view.getContext().obtainStyledAttributes(attrs);
        view.setBackgroundResource(ta.getResourceId(0, 0));
        ta.recycle();
    }

    // On API level 21 and higher, CookieManager will be set automatically, so there is nothing to do here.
    @Override
    public void prepareWebViewCookies(Context context) {}

    // A data of cookies may be lost when an application exists just after it was written.
    // On API level 21 and higher, this problem can be solved by using CookieManager.flush().
    @Override
    public void flushWebViewCookies() {
        CookieManager.getInstance().flush();
    }

    @Override
    public void setStatusBarColor(Window window, int color) {
        window.setStatusBarColor(color);
    }

    @Override
    public int getCameraCount() {
        CameraManager cameraManager = (CameraManager)AnkiDroidApp.getInstance().getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                return cameraManager.getCameraIdList().length;
            }
        } catch (CameraAccessException e) {
            Timber.e(e, "Unable to enumerate cameras");
        }
        return 0;
    }

    @Override
    public Object initTtsParams() {
        return new Bundle();
    }

    @Override
    public int speak(TextToSpeech tts, String text, int queueMode, Object ttsParams, String utteranceId) {
        return tts.speak(text, queueMode, (Bundle) ttsParams, utteranceId);
    }
}