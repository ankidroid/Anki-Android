/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
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

import java.io.File;
import java.net.URI;
import java.util.HashMap;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.ReadText;

/**
 * Class used to parse, load and play sound files on AnkiDroid.
 */
public class Sound {

    /**
     * Pattern used to identify the markers for sound files
     */
    public static Pattern sSoundPattern = Pattern.compile("\\[sound\\:([^\\[\\]]*)\\]");

    /**
     * Media player used to play the sounds
     */
    private static MediaPlayer sMediaPlayer;

	/**
	 * AudioManager to request/release audio focus
	 */
	private static AudioManager sAudioManager;

    /**
     * Stores sounds for the current card, key is for question/answer
     */
    private static HashMap<Integer, ArrayList<String>> sSoundPaths = new HashMap<Integer, ArrayList<String>>();

	/**
	 * Listener to handle audio focus. Currently blank because we're not respecting losing focus from other apps.
	 */
	private static OnAudioFocusChangeListener afChangeListener = new OnAudioFocusChangeListener() {
			public void onAudioFocusChange(int focusChange) {
			}
		};


    /* Prevent class from being instantiated */
    private Sound() {
    }


    // / Clears current sound paths; call before parseSounds() calls
    public static void resetSounds() {
        sSoundPaths.clear();
    }

    /**
     * The function addSounds() parses content for sound files, and stores entries to the filepaths for them, categorized as
     * belonging to the front (question) or back (answer) of cards. Note that all sounds embedded in the content will be given
     * the same categorization.
     * @param soundDir -- base path to the media files
     * @param content -- parsed for sound entries
     * @param qa -- the categorization of the sounds in the conent
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
            String sound = matcher.group(1);

            // Construct the sound path and store it
            String soundPath = soundDir + Uri.encode(sound);
            sSoundPaths.get(qa).add(soundPath);
        }
    }

    public static String expandSounds(String soundDir, String content, boolean ttsEnabled, int qa) {
        boolean soundAvailable = false;
        StringBuilder stringBuilder = new StringBuilder();
        String contentLeft = content;

        Log.i(AnkiDroidApp.TAG, "expandSounds");

        Matcher matcher = sSoundPattern.matcher(content);
        // While there is matches of the pattern for sound markers
        while (matcher.find()) {
            soundAvailable = true;
            // Get the sound file name
            String sound = matcher.group(1);

            // Construct the sound path
            String soundPath = soundDir + Uri.encode(sound);

            // Construct the new content, appending the substring from the beginning of the content left until the
            // beginning of the sound marker
            // and then appending the html code to add the play button
            String soundMarker = matcher.group();
            int markerStart = contentLeft.indexOf(soundMarker);
            stringBuilder.append(contentLeft.substring(0, markerStart));
            stringBuilder
                .append("<a onclick=\"window.ankidroid.playSound(this.title);\" title=\""
                        + soundPath
                        + "\"><span style=\"padding:5px;\"><img src=\"file:///android_asset/media_playback_start2.png\" /></span></a>");
            contentLeft = contentLeft.substring(markerStart + soundMarker.length());
            Log.i(AnkiDroidApp.TAG, "Content left = " + contentLeft);
        }
        // TODO: readd tts
//        if (!soundAvailable && ttsEnabled && !ReadText.getLanguage(qa).equals(ReadText.NO_TTS)) {
//            stringBuilder.append(content.substring(0, content.length() - 9));
//            stringBuilder
//                    .append("<a onclick=\"window.ankidroid.playSound(this.title);\" title=\"tts"
//                            + Integer.toString(qa)
//                            + Utils.stripHTML(content)
//                            + "\"><span style=\"padding:5px;display:inline-block;vertical-align:middle\"><img src=\"file:///android_asset/media_playback_start2.png\" /></span></a>");
//            contentLeft = "</p>";
//        }

        stringBuilder.append(contentLeft);

        return stringBuilder.toString();
    }


    /**
     * Plays the sounds for the given card side
     */
    public static void playSounds(int qa) {
        // If there are sounds to play for the current card, start with the first one
        if (sSoundPaths != null && sSoundPaths.containsKey(qa)) {
            playSound(sSoundPaths.get(qa).get(0), new PlayAllCompletionListener(qa));
        }
    }


    /**
     * Plays the given sound, sets playAllListener if available on media player to start next sound
     */
    public static void playSound(String soundPath, OnCompletionListener playAllListener) {
        Log.i(AnkiDroidApp.TAG, "Playing " + soundPath + " has listener? " + Boolean.toString(playAllListener != null));

        if (soundPath.substring(0, 3).equals("tts")) {
            // TODO: give information about did
//            ReadText.textToSpeech(soundPath.substring(4, soundPath.length()),
//                    Integer.parseInt(soundPath.substring(3, 4)));
        } else {
            if (sMediaPlayer == null)
                sMediaPlayer = new MediaPlayer();
            else
                sMediaPlayer.reset();

			if (sAudioManager == null)
				sAudioManager = (AudioManager) AnkiDroidApp.getInstance().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

            try {
                // soundPath is usually an URI, but Media player requires a path not url encoded
                URI soundURI = new URI(soundPath);
                soundPath = new File(soundURI).getAbsolutePath();
                sMediaPlayer.setDataSource(soundPath);
                sMediaPlayer.setVolume(AudioManager.STREAM_MUSIC, AudioManager.STREAM_MUSIC);
                sMediaPlayer.prepare();
                if (playAllListener != null)
                    sMediaPlayer.setOnCompletionListener(playAllListener);

                sAudioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

                sMediaPlayer.start();
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "playSounds - Error reproducing sound " + soundPath + " = " + e.getMessage());
                releaseSound();
            }
        }
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
            if (sSoundPaths.containsKey(mQa) && mNextToPlay < sSoundPaths.get(mQa).size())
                playSound(sSoundPaths.get(mQa).get(mNextToPlay++), this);
            else
                releaseSound();
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
			sAudioManager.abandonAudioFocus(afChangeListener);
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
}
