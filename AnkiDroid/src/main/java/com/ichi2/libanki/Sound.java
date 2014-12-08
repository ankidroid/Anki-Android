/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.libanki;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.VideoView;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.ReadText;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class used to parse, load and play sound files on AnkiDroid.
 */
public class Sound {

    /**
     * Pattern used to identify the markers for sound files
     */
    public static Pattern sSoundPattern = Pattern.compile("\\[sound\\:([^\\[\\]]*)\\]");

    /**
     * Pattern used to parse URI (according to http://tools.ietf.org/html/rfc3986#page-50)
     */
    private static Pattern sUriPattern = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?$");

    /**
     * Media player used to play the sounds
     */
    private static MediaPlayer sMediaPlayer;

    /**
     * AudioManager to request/release audio focus
     */
    private static AudioManager sAudioManager;

    /**
     * OnCompletionListener so that external video player can notify to play next sound
     */
    private static OnCompletionListener sPlayAllListener;

    /**
     * Weak reference to the activity which is attempting to play the sound
     */
    private static WeakReference<Activity> sCallingActivity;

    /**
     * Subset Flags: Flags that indicate the subset of sounds to involve
     */
    public static final int  SOUNDS_QUESTION = 0;
    public static final int  SOUNDS_ANSWER = 1;
    public static final int  SOUNDS_QUESTION_AND_ANSWER = 2;

    /**
     * sSoundPaths: Stores sounds for the current card, key is one of the subset flags. It is intended that it not contain empty lists, and code assumes this will be true.
     */
    private static HashMap<Integer, ArrayList<String>> sSoundPaths = new HashMap<Integer, ArrayList<String>>();


    /* Prevent class from being instantiated */
    private Sound() {
    }


    // / Clears current sound paths; call before parseSounds() calls
    public static void resetSounds() {
        sSoundPaths.clear();
    }

    /**
     * resetSounds removes lists of sounds
     * @param which -- One of the subset flags, such as Sound.SOUNDS_QUESTION
     */
    public static void resetSounds(int which) {
        sSoundPaths.remove(which);
    }


    /**
     * The function addSounds() parses content for sound files, and stores entries to the filepaths for them,
     * categorized as belonging to the front (question) or back (answer) of cards. Note that all sounds embedded in
     * the content will be given the same base categorization of question or answer. Additionally, the result is to be
     * sorted by the order of appearance on the card.
     * @param soundDir -- base path to the media files
     * @param content -- parsed for sound entries, the entries expected in display order
     * @param qa -- the base categorization of the sounds in the content, Sound.SOUNDS_QUESTION or Sound.SOUNDS_ANSWER
     */
    public static void addSounds(String soundDir, String content, int qa) {
        Matcher matcher = sSoundPattern.matcher(content);
        // While there is matches of the pattern for sound markers
        while (matcher.find()) {
            // Create appropriate list if needed; list must not be empty so long as code does no check
            if (!sSoundPaths.containsKey(qa)) {
                sSoundPaths.put(qa, new ArrayList<String>());
            }

            // Get the sound file name
            String sound = matcher.group(1).trim();

            // Construct the sound path and store it
            sSoundPaths.get(qa).add(getSoundPath(soundDir, sound));
        }
    }

    /**
     * makeQuestionAnswerSoundList creates a single list of both the question and answer audio only if it does not
     * already exist. It's intended for lazy evaluation, only in the rare cases when both sides are fully played
     * together, which even when configured as supported may not be instigated
     * @return True if a non-null list was created, or false otherwise
     */
    public static Boolean makeQuestionAnswerList() {
        // if combined list already exists, don't recreate
        if (sSoundPaths.containsKey(Sound.SOUNDS_QUESTION_AND_ANSWER)) {
            return false; // combined list already exists
        }

        // make combined list only if necessary to avoid an empty combined list
        if (sSoundPaths.containsKey(Sound.SOUNDS_QUESTION) || sSoundPaths.containsKey(Sound.SOUNDS_ANSWER)) {
            // some list exists to place into combined list
            sSoundPaths.put(Sound.SOUNDS_QUESTION_AND_ANSWER, new ArrayList<String>());
        } else { // no need to make list
            return false;
        }

        ArrayList<String> combinedSounds = sSoundPaths.get(Sound.SOUNDS_QUESTION_AND_ANSWER);

        if (sSoundPaths.containsKey(Sound.SOUNDS_QUESTION)) {
            combinedSounds.addAll(sSoundPaths.get(Sound.SOUNDS_QUESTION));
        }
        if (sSoundPaths.containsKey(Sound.SOUNDS_ANSWER)) {
            combinedSounds.addAll(sSoundPaths.get(Sound.SOUNDS_ANSWER));
        }

        return true;
    }

    /**
     * expandSounds takes content with embedded sound file placeholders and expands them to reference the actual media
     * file
     *
     * @param soundDir -- the base path of the media files
     * @param content -- card content to be rendered that may contain embedded audio
     * @return -- the same content but in a format that will render working play buttons when audio was embedded
     */
    public static String expandSounds(String soundDir, String content) {
        StringBuilder stringBuilder = new StringBuilder();
        String contentLeft = content;

        Log.i(AnkiDroidApp.TAG, "expandSounds");

        Matcher matcher = sSoundPattern.matcher(content);
        // While there is matches of the pattern for sound markers
        while (matcher.find()) {
            // Get the sound file name
            String sound = matcher.group(1).trim();

            // Construct the sound path
            String soundPath = getSoundPath(soundDir, sound);

            // Construct the new content, appending the substring from the beginning of the content left until the
            // beginning of the sound marker
            // and then appending the html code to add the play button
            String button;
            if (AnkiDroidApp.SDK_VERSION >= Build.VERSION_CODES.GINGERBREAD_MR1) {
                button = "<img src='file:///android_res/drawable/inline_play_button.png' width='32' height='32' />";
            } else {
                button = "<img src='file:///android_asset/media_playback_start2.png' />";
            }
            String soundMarker = matcher.group();
            int markerStart = contentLeft.indexOf(soundMarker);
            stringBuilder.append(contentLeft.substring(0, markerStart));
            stringBuilder.append("<a class='replaybutton' href='playsound:" + soundPath + "'>"
                        + "<span style='padding:5px;'>"+ button
                        + "</span></a>");
            contentLeft = contentLeft.substring(markerStart + soundMarker.length());
            Log.i(AnkiDroidApp.TAG, "Content left = " + contentLeft);
        }

        // unused code related to tts support taken out after v2.2alpha55
        // if/when tts support is considered complete, these comment lines serve no purpose

        stringBuilder.append(contentLeft);

        return stringBuilder.toString();
    }


    /**
     * Plays the sounds for the indicated sides
     * @param qa -- One of Sound.SOUNDS_QUESTION, Sound.SOUNDS_ANSWER, or Sound.SOUNDS_QUESTION_AND_ANSWER
     */
    public static void playSounds(int qa, WeakReference<Activity> activityRef) {
        sCallingActivity = activityRef;
        // If there are sounds to play for the current card, start with the first one
        if (sSoundPaths != null && sSoundPaths.containsKey(qa)) {
            playSound(sSoundPaths.get(qa).get(0), new PlayAllCompletionListener(qa));
        } else if (sSoundPaths != null && qa == Sound.SOUNDS_QUESTION_AND_ANSWER) {
            if (Sound.makeQuestionAnswerList()) {
                playSound(sSoundPaths.get(qa).get(0), new PlayAllCompletionListener(qa));
            }
        }
    }

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media
     * @param soundPath
     * @param playAllListener
     */
    public static void playSound(String soundPath, OnCompletionListener playAllListener) {
        playSound(soundPath, playAllListener, null);
    }

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media.
     * If videoView is null and the media is a video, then a request is sent to start the VideoPlayer Activity
     * @param soundPath
     * @param playAllListener
     * @param videoView
     */
    @SuppressLint("NewApi")
    public static void playSound(String soundPath, OnCompletionListener playAllListener, final VideoView videoView) {
        Log.i(AnkiDroidApp.TAG, "Playing " + soundPath + " has listener? " + Boolean.toString(playAllListener != null));

        if (soundPath.substring(0, 3).equals("tts")) {
            // TODO: give information about did
//            ReadText.textToSpeech(soundPath.substring(4, soundPath.length()),
//                    Integer.parseInt(soundPath.substring(3, 4)));
        } else {
            // Check if file is video
            final boolean isVideo;
            if (AnkiDroidApp.SDK_VERSION > 7){
                String realPath;
                try {
                    realPath = (new File(soundPath.replace("file:///",""))).getCanonicalPath();
                } catch (IOException e1) {
                    realPath = soundPath;
                }
                isVideo = ThumbnailUtils.createVideoThumbnail(realPath, MediaStore.Images.Thumbnails.MINI_KIND) != null;
            } else {
                // Don't bother supporting video on Android 2.1
                isVideo = false;
            }
            // If video file but no SurfaceHolder provided then ask 
            // AbstractFlashcardViewer to provide a VideoView holder
            if (isVideo && videoView == null && sCallingActivity.get() != null) {
                sPlayAllListener = playAllListener;
                ((AbstractFlashcardViewer) sCallingActivity.get()).playVideo(soundPath);
                return;
            }
            // Play media
            try {
                // Create media player
                if (sMediaPlayer == null) {
                    sMediaPlayer = new MediaPlayer();
                } else {
                    sMediaPlayer.reset();
                }
                if (sAudioManager == null) {
                    sAudioManager = (AudioManager) AnkiDroidApp.getInstance().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                }
                // Provide a VideoView to the MediaPlayer if valid video file
                if (isVideo && videoView != null) {
                    sMediaPlayer.setDisplay(videoView.getHolder());
                    sMediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                        @Override
                        public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                            configureVideo(videoView, width, height);
                        }
                    });
                }
                // Setup the MediaPlayer
                Uri soundUri = Uri.parse(soundPath);
                sMediaPlayer.setDataSource(AnkiDroidApp.getInstance().getApplicationContext(),
                                           soundUri);
                sMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                sMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        sMediaPlayer.start();
                    }
                });
                if (playAllListener != null) {
                    sMediaPlayer.setOnCompletionListener(playAllListener);
                }
                sMediaPlayer.prepareAsync();
                AnkiDroidApp.getCompat().requestAudioFocus(sAudioManager);
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "playSounds - Error reproducing sound " + soundPath + " = " + e.getMessage());
                releaseSound();
            }
        }
    }

    private static void configureVideo(VideoView videoView, int videoWidth, int videoHeight) {
        // get the display
        Context context = AnkiDroidApp.getInstance().getApplicationContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        // adjust the size of the video so it fits on the screen
        float videoProportion = (float) videoWidth / (float) videoHeight;
        int screenWidth = display.getWidth();
        int screenHeight = display.getHeight();
        float screenProportion = (float) screenWidth / (float) screenHeight;
        android.view.ViewGroup.LayoutParams lp = videoView.getLayoutParams();

        if (videoProportion > screenProportion) {
            lp.width = screenWidth;
            lp.height = (int) ((float) screenWidth / videoProportion);
        } else {
            lp.width = (int) (videoProportion * (float) screenHeight);
            lp.height = screenHeight;
        }
        videoView.setLayoutParams(lp);
    }

    public static void notifyConfigurationChanged(VideoView videoView) {
        configureVideo(videoView, sMediaPlayer.getVideoWidth(), sMediaPlayer.getVideoHeight());
    }

    /**
     * Class used to play all sounds for a given card side
     */
    private static final class PlayAllCompletionListener implements OnCompletionListener {

        /**
         * Question/Answer
         */
        private final int mQa;

        /**
         * next sound to play (onCompletion() is first called after the first (0) has been played)
         */
        private int mNextToPlay = 1;


        private PlayAllCompletionListener(int qa) {
            mQa = qa;
        }


        @Override
        public void onCompletion(MediaPlayer mp) {
            // If there is still more sounds to play for the current card, play the next one
            if (sSoundPaths.containsKey(mQa) && mNextToPlay < sSoundPaths.get(mQa).size()) {
                playSound(sSoundPaths.get(mQa).get(mNextToPlay++), this);
            } else {
                releaseSound();
            }
        }
    }

    /**
     * Releases the sound.
     */
    private static void releaseSound() {
        if (sMediaPlayer != null) {
            sMediaPlayer.release();
            sMediaPlayer = null;
        }
        if (sAudioManager != null) {
            AnkiDroidApp.getCompat().abandonAudioFocus(sAudioManager);
            sAudioManager = null;
        }
    }

    /**
     * Stops the playing sounds.
     */
    public static void stopSounds() {
        if (sMediaPlayer != null) {
            sMediaPlayer.stop();
            releaseSound();
        }
        ReadText.stopTts();
    }

    /**
     * @param soundDir -- base path to the media files.
     * @param sound -- path to the sound file from the card content.
     * @return absolute URI to the sound file.
     */
    private static String getSoundPath(String soundDir, String sound) {
        if (hasURIScheme(sound)) {
            return sound;
        }
        return soundDir + Uri.encode(sound);
    }

    /**
     * @param path -- path to the sound file from the card content.
     * @return true if path is well-formed URI and contains URI scheme.
     */
    private static boolean hasURIScheme(String path) {
        Matcher uriMatcher = sUriPattern.matcher(path.trim());
        return uriMatcher.matches() && uriMatcher.group(2) != null;
    }

    public static OnCompletionListener getMediaCompletionListener() {
        return sPlayAllListener;
    }

    public static boolean hasQuestion() {
        return sSoundPaths.containsKey(Sound.SOUNDS_QUESTION);
    }
    public static boolean hasAnswer() {
        return sSoundPaths.containsKey(Sound.SOUNDS_ANSWER);
    }
}
