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
import android.provider.MediaStore;

import android.view.Display;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.VideoView;

import com.ichi2.anki.AbstractFlashcardViewer;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.ReadText;
import com.ichi2.utils.StringUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import timber.log.Timber;


//NICE_TO_HAVE: Abstract, then add tests fir #6111
/**
 * Class used to parse, load and play sound files on AnkiDroid.
 */
@SuppressWarnings({"PMD.NPathComplexity","PMD.CollapsibleIfStatements"})
public class Sound {

    /**
     * Pattern used to identify the markers for sound files
     */
    public static final Pattern sSoundPattern = Pattern.compile("\\[sound:([^\\[\\]]*)]");

    /**
     * Pattern used to parse URI (according to http://tools.ietf.org/html/rfc3986#page-50)
     */
    private static final Pattern sUriPattern = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?$");

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
    public enum SoundSide {
        QUESTION(0),
        ANSWER(1),
        QUESTION_AND_ANSWER(2);

        private final int mInt;
        SoundSide(int i) {
            mInt = i;
        }
        public int getInt() {
            return mInt;
        }
    }



    /**
     * Stores sounds for the current card, key is one of the subset flags. It is intended that it not contain empty lists, and code assumes this will be true.
     */
    private final HashMap<SoundSide, ArrayList<String>> mSoundPaths = new HashMap<>();


    /**
     * Whitelist for video extensions
     */
    private static final String[] VIDEO_WHITELIST = {"3gp", "mp4", "webm", "mkv", "flv"};

    /**
     * Listener to handle audio focus. Currently blank because we're not respecting losing focus from other apps.
     */
    private static final AudioManager.OnAudioFocusChangeListener afChangeListener = focusChange -> {
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
     * @param qa -- the base categorization of the sounds in the content, SoundSide.SOUNDS_QUESTION or SoundSide.SOUNDS_ANSWER
     */
    public void addSounds(String soundDir, String content, SoundSide qa) {
        Matcher matcher = sSoundPattern.matcher(content);
        // While there is matches of the pattern for sound markers
        while (matcher.find()) {
            // Create appropriate list if needed; list must not be empty so long as code does no check
            if (!mSoundPaths.containsKey(qa)) {
                mSoundPaths.put(qa, new ArrayList<>(0));
            }

            // Get the sound file name
            String sound = matcher.group(1);

            // Construct the sound path and store it
            Timber.d("Adding Sound to side: %s", qa);
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
        if (mSoundPaths.containsKey(SoundSide.QUESTION_AND_ANSWER)) {
            return false; // combined list already exists
        }

        // make combined list only if necessary to avoid an empty combined list
        if (mSoundPaths.containsKey(SoundSide.QUESTION) || mSoundPaths.containsKey(SoundSide.ANSWER)) {
            // some list exists to place into combined list
            mSoundPaths.put(SoundSide.QUESTION_AND_ANSWER, new ArrayList<>(0));
        } else { // no need to make list
            return false;
        }

        ArrayList<String> combinedSounds = mSoundPaths.get(SoundSide.QUESTION_AND_ANSWER);

        if (mSoundPaths.containsKey(SoundSide.QUESTION)) {
            combinedSounds.addAll(mSoundPaths.get(SoundSide.QUESTION));
        }
        if (mSoundPaths.containsKey(SoundSide.ANSWER)) {
            combinedSounds.addAll(mSoundPaths.get(SoundSide.ANSWER));
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
            String sound = matcher.group(1);

            // Construct the sound path
            String soundPath = getSoundPath(soundDir, sound);

            // Construct the new content, appending the substring from the beginning of the content left until the
            // beginning of the sound marker
            // and then appending the html code to add the play button
            String button = "<svg viewBox=\"0 0 32 32\"><polygon points=\"11,25 25,16 11,7\"/>Replay</svg>";
            String soundMarker = matcher.group();
            int markerStart = contentLeft.indexOf(soundMarker);
            stringBuilder.append(contentLeft.substring(0, markerStart));
            // The <span> around the button (SVG or PNG image) is needed to make the vertical alignment work.
            stringBuilder.append("<a class='replaybutton' href=\"playsound:").append(soundPath).append("\">")
                    .append("<span>").append(button)
                    .append("</span></a>");
            contentLeft = contentLeft.substring(markerStart + soundMarker.length());
            Timber.v("Content left = %s", contentLeft);
        }

        // unused code related to tts support taken out after v2.2alpha55
        // if/when tts support is considered complete, these comment lines serve no purpose

        stringBuilder.append(contentLeft);

        return stringBuilder.toString();
    }


    /**
     * Plays the sounds for the indicated sides
     * @param qa -- One of SoundSide.SOUNDS_QUESTION, SoundSide.SOUNDS_ANSWER, or SoundSide.SOUNDS_QUESTION_AND_ANSWER
     */
    public void playSounds(SoundSide qa, @Nullable OnErrorListener errorListener) {
        // If there are sounds to play for the current card, start with the first one
        if (mSoundPaths != null && mSoundPaths.containsKey(qa)) {
            Timber.d("playSounds %s", qa);
            playSoundInternal(mSoundPaths.get(qa).get(0), new PlayAllCompletionListener(qa, errorListener), null, errorListener);
        } else if (mSoundPaths != null && qa == SoundSide.QUESTION_AND_ANSWER) {
            if (makeQuestionAnswerList()) {
                Timber.d("playSounds: playing both question and answer");
                playSoundInternal(mSoundPaths.get(qa).get(0), new PlayAllCompletionListener(qa, errorListener), null, errorListener);
            } else {
                Timber.d("playSounds: No question answer list, not playing sound");
            }
        }
    }

    /**
     * Returns length in milliseconds.
     * @param qa -- One of SoundSide.SOUNDS_QUESTION, SoundSide.SOUNDS_ANSWER, or SoundSide.SOUNDS_QUESTION_AND_ANSWER
     */
    public long getSoundsLength(SoundSide qa) {
        long length = 0;
        if (mSoundPaths != null && (qa == SoundSide.QUESTION_AND_ANSWER && makeQuestionAnswerList() || mSoundPaths.containsKey(qa))) {
            MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
            for (String uri_string : mSoundPaths.get(qa)) {
                Uri soundUri = Uri.parse(uri_string);
                try {
                    metaRetriever.setDataSource(AnkiDroidApp.getInstance().getApplicationContext(), soundUri);
                    length += Long.parseLong(metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                } catch (Exception e) {
                    Timber.e(e, "metaRetriever - Error setting Data Source for mediaRetriever (media doesn't exist or forbidden?).");
                }
            }
        }
        return length;
    }

    /**
     * Plays the given sound or video and sets playAllListener if available on media player to start next media.
     * If videoView is null and the media is a video, then a request is sent to start the VideoPlayer Activity
     */
    public void playSound(String soundPath, OnCompletionListener playAllListener, final VideoView videoView, @Nullable OnErrorListener errorListener) {
        Timber.d("Playing single sound");
        SingleSoundCompletionListener completionListener = new SingleSoundCompletionListener(playAllListener);
        playSoundInternal(soundPath, completionListener, videoView, errorListener);
    }

    /** Plays a sound without ensuring that the playAllListener will release the audio */
    @SuppressWarnings({"PMD.EmptyIfStmt","PMD.CollapsibleIfStatements","deprecation"}) // audio API deprecation tracked on github as #5022
    private void playSoundInternal(String soundPath, OnCompletionListener playAllListener, VideoView videoView, OnErrorListener errorListener) {
        Timber.d("Playing %s has listener? %b", soundPath, playAllListener != null);
        Uri soundUri = Uri.parse(soundPath);

        final OnErrorListener errorHandler = errorListener == null ?
                (mp, what, extra, path) -> {
                    Timber.w("Media Error: (%d, %d). Calling OnCompletionListener", what, extra);
                    return false;
                }
            : errorListener;

        if ("tts".equals(soundPath.substring(0, 3))) {
            // TODO: give information about did
//            ReadText.textToSpeech(soundPath.substring(4, soundPath.length()),
//                    Integer.parseInt(soundPath.substring(3, 4)));
        } else {
            // Check if the file extension is that of a known video format
            final String extension = soundPath.substring(soundPath.lastIndexOf(".") + 1).toLowerCase(Locale.getDefault());
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
                Timber.d("Requesting AbstractFlashcardViewer play video - no SurfaceHolder");
                mPlayAllListener = playAllListener;
                ((AbstractFlashcardViewer) mCallingActivity.get()).playVideo(soundPath);
                return;
            }
            // Play media
            try {
                // Create media player
                if (mMediaPlayer == null) {
                    Timber.d("Creating media player for playback");
                    mMediaPlayer = new MediaPlayer();
                } else {
                    Timber.d("Resetting media for playback");
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
                mMediaPlayer.setOnErrorListener((mp, which, extra) -> errorHandler.onError(mp, which, extra, soundPath));
                // Setup the MediaPlayer
                mMediaPlayer.setDataSource(AnkiDroidApp.getInstance().getApplicationContext(), soundUri);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setOnPreparedListener(mp -> {
                    Timber.d("Starting media player");
                    mMediaPlayer.start();
                });
                if (playAllListener != null) {
                    mMediaPlayer.setOnCompletionListener(playAllListener);
                }
                mMediaPlayer.prepareAsync();
                Timber.d("Requesting audio focus");
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

    /** #5414 - Ensures playing a single sound performs cleanup */
    private final class SingleSoundCompletionListener implements OnCompletionListener {
        @Nullable
        private final OnCompletionListener userCallback;

        public SingleSoundCompletionListener(@Nullable OnCompletionListener userCallback) {
            this.userCallback = userCallback;
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
            Timber.d("Single Sound completed");
            if (userCallback != null) {
                userCallback.onCompletion(mp);
            } else {
                releaseSound();
            }
        }
    }

    /**
     * Class used to play all sounds for a given card side
     */
    private final class PlayAllCompletionListener implements OnCompletionListener {

        /**
         * Question/Answer
         */
        private final SoundSide mQa;
        private final OnErrorListener mErrorListener;

        /**
         * next sound to play (onCompletion() is first called after the first (0) has been played)
         */
        private int mNextToPlay = 1;


        private PlayAllCompletionListener(SoundSide qa, @Nullable OnErrorListener errorListener) {
            mQa = qa;
            mErrorListener = errorListener;
        }


        @Override
        public void onCompletion(MediaPlayer mp) {
            // If there is still more sounds to play for the current card, play the next one
            if (mSoundPaths.containsKey(mQa) && mNextToPlay < mSoundPaths.get(mQa).size()) {
                Timber.i("Play all: Playing next sound");
                playSound(mSoundPaths.get(mQa).get(mNextToPlay++), this, null, mErrorListener);
            } else {
                Timber.i("Play all: Completed - releasing sound");
                releaseSound();
            }
        }
    }

    /**
     * Releases the sound.
     */
    @SuppressWarnings("deprecation") // Tracked on github as #5022
    private void releaseSound() {
        Timber.d("Releasing sounds and abandoning audio focus");
        if (mMediaPlayer != null) {
            //Required to remove warning: "mediaplayer went away with unhandled events"
            //https://stackoverflow.com/questions/9609479/android-mediaplayer-went-away-with-unhandled-events
            mMediaPlayer.reset();
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
        String trimmedSound = sound.trim();
        if (hasURIScheme(trimmedSound)) {
            return trimmedSound;
        }

        return soundDir + Uri.encode(StringUtil.trimRight(sound));
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
        return mSoundPaths.containsKey(SoundSide.QUESTION);
    }
    public boolean hasAnswer() {
        return mSoundPaths.containsKey(SoundSide.ANSWER);
    }

    public interface OnErrorListener {
        boolean onError(MediaPlayer mp, int which, int extra, String path);
    }
}
