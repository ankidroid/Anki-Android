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

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import android.view.Display;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.VideoView;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.ReadText;
import com.ichi2.compat.CompatHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Class used to parse, load and play sound files on AnkiDroid.
 */
@SuppressWarnings({"PMD.NPathComplexity","PMD.CollapsibleIfStatements"})
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
    private MediaPlayer mMediaPlayer;

    /**
     * AudioManager to request/release audio focus
     */
    private AudioManager mAudioManager;

    /**
     * OnCompletionListener so that external video player can notify to play next sound
     */
    private static OnCompletionListener mPlayAllListener;

    /**
     * Weak reference to the activity which is attempting to play the sound
     */
    private WeakReference<Activity> mCallingActivity;

    /**
     * Subset Flags: Flags that indicate the subset of sounds to involve
     */
    public static final int  SOUNDS_QUESTION = 0;
    public static final int  SOUNDS_ANSWER = 1;
    public static final int  SOUNDS_QUESTION_AND_ANSWER = 2;

    /**
     * Stores sounds for the current card, key is one of the subset flags. It is intended that it not contain empty lists, and code assumes this will be true.
     */
    private HashMap<Integer, ArrayList<String>> mSoundPaths = new HashMap<>();


    /**
     * Whitelist for video extensions
     */
    private static final String[] VIDEO_WHITELIST = {"3gp", "mp4", "webm", "mkv", "flv"};

    /**
     * Listener to handle audio focus. Currently blank because we're not respecting losing focus from other apps.
     */
    private static AudioManager.OnAudioFocusChangeListener afChangeListener = focusChange -> {
    };


    // Clears current sound paths; call before parseSounds() calls
    public void resetSounds() {
        mSoundPaths.clear();
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
    public void addSounds(String soundDir, String content, int qa) {
        Matcher matcher = sSoundPattern.matcher(content);
        // While there is matches of the pattern for sound markers
        while (matcher.find()) {
            // Create appropriate list if needed; list must not be empty so long as code does no check
            if (!mSoundPaths.containsKey(qa)) {
                mSoundPaths.put(qa, new ArrayList<>());
            }

            // Get the sound file name
            String sound = matcher.group(1).trim();

            // Construct the sound path and store it
            mSoundPaths.get(qa).add(getSoundPath(soundDir, sound));
        }
    }

    /**
     * makeQuestionAnswerSoundList creates a single list of both the question and answer audio only if it does not
     * already exist. It's intended for lazy evaluation, only in the rare cases when both sides are fully played
     * together, which even when configured as supported may not be instigated
     * @return True if a non-null list was created, or false otherwise
     */
    private Boolean makeQuestionAnswerList() {
        // if combined list already exists, don't recreate
        if (mSoundPaths.containsKey(Sound.SOUNDS_QUESTION_AND_ANSWER)) {
            return false; // combined list already exists
        }

        // make combined list only if necessary to avoid an empty combined list
        if (mSoundPaths.containsKey(Sound.SOUNDS_QUESTION) || mSoundPaths.containsKey(Sound.SOUNDS_ANSWER)) {
            // some list exists to place into combined list
            mSoundPaths.put(Sound.SOUNDS_QUESTION_AND_ANSWER, new ArrayList<>());
        } else { // no need to make list
            return false;
        }

        ArrayList<String> combinedSounds = mSoundPaths.get(Sound.SOUNDS_QUESTION_AND_ANSWER);

        if (mSoundPaths.containsKey(Sound.SOUNDS_QUESTION)) {
            combinedSounds.addAll(mSoundPaths.get(Sound.SOUNDS_QUESTION));
        }
        if (mSoundPaths.containsKey(Sound.SOUNDS_ANSWER)) {
            combinedSounds.addAll(mSoundPaths.get(Sound.SOUNDS_ANSWER));
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

        Timber.d("expandSounds");

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
            if (CompatHelper.getSdkVersion() >= Build.VERSION_CODES.HONEYCOMB) {
                button = "<svg viewBox=\"0 0 32 32\"><polygon points=\"11,25 25,16 11,7\"/>Replay</svg>";
            } else {
                button = "<img src='file:///android_asset/inline_play_button.png' />";
            }
            String soundMarker = matcher.group();
            int markerStart = contentLeft.indexOf(soundMarker);
            stringBuilder.append(contentLeft.substring(0, markerStart));
            // The <span> around the button (SVG or PNG image) is needed to make the vertical alignment work.
            stringBuilder.append("<a class='replaybutton' href=\"playsound:").append(soundPath).append("\">")
                    .append("<span>").append(button)
                    .append("</span></a>");
            contentLeft = contentLeft.substring(markerStart + soundMarker.length());
            Timber.d("Content left = %s", contentLeft);
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
    public void playSounds(int qa) {
        // If there are sounds to play for the current card, start with the first one
        if (mSoundPaths != null && mSoundPaths.containsKey(qa)) {
            playSound(mSoundPaths.get(qa).get(0), new PlayAllCompletionListener(qa));
        } else if (mSoundPaths != null && qa == Sound.SOUNDS_QUESTION_AND_ANSWER) {
            if (makeQuestionAnswerList()) {
                playSound(mSoundPaths.get(qa).get(0), new PlayAllCompletionListener(qa));
            }
        }
    }

    /**
     * Returns length in milliseconds.
     * @param qa -- One of Sound.SOUNDS_QUESTION, Sound.SOUNDS_ANSWER, or Sound.SOUNDS_QUESTION_AND_ANSWER
     */
    public long getSoundsLength(int qa) {
        long length = 0;
        if (mSoundPaths != null && (qa == Sound.SOUNDS_QUESTION_AND_ANSWER && makeQuestionAnswerList() || mSoundPaths.containsKey(qa))) {
            MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
            for (String uri_string : mSoundPaths.get(qa)) {
                Uri soundUri = Uri.parse(uri_string);
                try {
                    metaRetriever.setDataSource(AnkiDroidApp.getInstance().getApplicationContext(), soundUri);
                    length += Long.parseLong(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                } catch (IllegalArgumentException iae) {
                    Timber.e(iae, "metaRetriever - Error setting Data Source for mediaRetriever (media doesn't exist).");
                }
            }
        }
        return length;
    }

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media
     */
    public void playSound(String soundPath, OnCompletionListener playAllListener) {
        playSound(soundPath, playAllListener, null);
    }

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media.
     * If videoView is null and the media is a video, then a request is sent to start the VideoPlayer Activity
     */
    @SuppressWarnings({"PMD.EmptyIfStmt","PMD.CollapsibleIfStatements","deprecation"}) // audio API deprecation tracked on github as #5022
    public void playSound(String soundPath, OnCompletionListener playAllListener, final VideoView videoView) {
        Timber.d("Playing %s has listener? %b", soundPath, playAllListener != null);
        Uri soundUri = Uri.parse(soundPath);

        if (soundPath.substring(0, 3).equals("tts")) {
            // TODO: give information about did
//            ReadText.textToSpeech(soundPath.substring(4, soundPath.length()),
//                    Integer.parseInt(soundPath.substring(3, 4)));
        } else {
            // Check if the file extension is that of a known video format
            final String extension = soundPath.substring(soundPath.lastIndexOf(".") + 1).toLowerCase();
            boolean isVideo = Arrays.asList(VIDEO_WHITELIST).contains(extension);
            if (!isVideo) {
                final String guessedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                isVideo = (guessedType != null) && guessedType.startsWith("video/");
            }
            // Also check that there is a video thumbnail, as some formats like mp4 can be audio only
            isVideo = isVideo &&
                ThumbnailUtils.createVideoThumbnail(soundUri.getPath(), MediaStore.Images.Thumbnails.MINI_KIND) != null;
            // No thumbnail: no video after all. (Or maybe not a video we can handle on the specific device.)
            // If video file but no SurfaceHolder provided then ask AbstractFlashcardViewer to provide a VideoView
            // holder
            if (isVideo && videoView == null && mCallingActivity != null && mCallingActivity.get() != null) {
                mPlayAllListener = playAllListener;
                ((AbstractFlashcardViewer) mCallingActivity.get()).playVideo(soundPath);
                return;
            }
            // Play media
            try {
                // Create media player
                if (mMediaPlayer == null) {
                    mMediaPlayer = new MediaPlayer();
                } else {
                    mMediaPlayer.reset();
                }
                if (mAudioManager == null) {
                    mAudioManager = (AudioManager) AnkiDroidApp.getInstance().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                }
                // Provide a VideoView to the MediaPlayer if valid video file
                if (isVideo && videoView != null) {
                    mMediaPlayer.setDisplay(videoView.getHolder());
                    mMediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> configureVideo(videoView, width, height));
                }
                // Setup the MediaPlayer
                mMediaPlayer.setDataSource(AnkiDroidApp.getInstance().getApplicationContext(), soundUri);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setOnPreparedListener(mp -> mMediaPlayer.start());
                if (playAllListener != null) {
                    mMediaPlayer.setOnCompletionListener(playAllListener);
                }
                mMediaPlayer.prepareAsync();
                mAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } catch (Exception e) {
                Timber.e(e, "playSounds - Error reproducing sound %s", soundPath);
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
        Point point = new Point();
        display.getSize(point);
        int screenWidth = point.x;
        int screenHeight = point.y;
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

    public void notifyConfigurationChanged(VideoView videoView) {
        if (mMediaPlayer != null) {
            configureVideo(videoView, mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
        }
    }

    /**
     * Class used to play all sounds for a given card side
     */
    private final class PlayAllCompletionListener implements OnCompletionListener {

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
            if (mSoundPaths.containsKey(mQa) && mNextToPlay < mSoundPaths.get(mQa).size()) {
                playSound(mSoundPaths.get(mQa).get(mNextToPlay++), this);
            } else {
                releaseSound();
            }
        }
    }

    /**
     * Releases the sound.
     */
    @SuppressWarnings("deprecation") // Tracked on github as #5022
    private void releaseSound() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mAudioManager != null) {
            mAudioManager.abandonAudioFocus(afChangeListener);
            mAudioManager = null;
        }
    }

    /**
     * Stops the playing sounds.
     */
    public void stopSounds() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
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

    /**
     * Set the context for the calling activity (necessary for playing videos)
     */
    public void setContext(WeakReference<Activity> activityRef) {
        mCallingActivity = activityRef;
    }

    public OnCompletionListener getMediaCompletionListener() {
        return mPlayAllListener;
    }

    public boolean hasQuestion() {
        return mSoundPaths.containsKey(Sound.SOUNDS_QUESTION);
    }
    public boolean hasAnswer() {
        return mSoundPaths.containsKey(Sound.SOUNDS_ANSWER);
    }
}
