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

package com.ichi2.anki;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.Log;

import java.util.ArrayList;
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
     * Media player used to play the sounds
     */
    private static MediaPlayer sMediaPlayer;

    /**
     * ArrayList to store the current sound paths
     */
    private static ArrayList<String> sSoundPaths;

    /**
     * Counter of the number of sounds played out of the total number of sounds in soundPaths
     */
    private static int sNumSoundsPlayed;

    /* Prevent class from being instantiated */
    private Sound() { }


    public static String parseSounds(String deckFilename, String content, boolean ttsEnabled, int qa) {
    	boolean soundAvailable = false;
        StringBuilder stringBuilder = new StringBuilder();
        String contentLeft = content;

        Log.i(AnkiDroidApp.TAG, "parseSounds");
        sSoundPaths = new ArrayList<String>();
        Matcher matcher = sSoundPattern.matcher(content);
        // While there is matches of the pattern for sound markers
        while (matcher.find()) {
        	soundAvailable = true;
            // Get the sound file name
            String sound = matcher.group(1);
            // Log.i(AnkiDroidApp.TAG, "Sound " + matcher.groupCount() + ": " + sound);

            // Construct the sound path and store it
            String soundPath = deckFilename.replace(".anki", ".media/") + sound;
            // Log.i(AnkiDroidApp.TAG, "parseSounds - soundPath = " + soundPath);
            sSoundPaths.add(soundPath);

            // Construct the new content, appending the substring from the beginning of the content left until the
            // beginning of the sound marker
            // and then appending the html code to add the play button
            String soundMarker = matcher.group();
            int markerStart = contentLeft.indexOf(soundMarker);
            stringBuilder.append(contentLeft.substring(0, markerStart));        
            stringBuilder
                .append("<a onclick=\"window.interface.playSound(this.title);\" title=\""
                        + soundPath
                        + "\"><span style=\"padding:5px;display:inline-block;vertical-align:middle\"><img src=\"file:///android_asset/media_playback_start2.png\" /></span></a>");
            contentLeft = contentLeft.substring(markerStart + soundMarker.length());
            // Log.i(AnkiDroidApp.TAG, "Content left = " + contentLeft);
        }
        if (!soundAvailable && ttsEnabled) {
            stringBuilder.append(content.substring(0, content.length() - 9));        
            stringBuilder
                .append("<a onclick=\"window.interface.playSound(this.title);\" title=\"tts" + Integer.toString(qa)
                		+ Utils.stripHTML(content)
                        + "\"><span style=\"padding:5px;display:inline-block;vertical-align:middle\"><img src=\"file:///android_asset/media_playback_start2.png\" /></span></a>");
            contentLeft = "</p>";
        }

        stringBuilder.append(contentLeft);

        return stringBuilder.toString();
    }


    /**
     * Plays the sounds stored on the paths indicated by mSoundPaths.
     */
    public static void playSounds(String text, int qa) {
        // If there are sounds to play for the current card, play the first one
    	if (sSoundPaths != null && sSoundPaths.size() > 0) {
            sNumSoundsPlayed = 0;
            playSound(sNumSoundsPlayed);
        } else if (text != null && Integer.valueOf(android.os.Build.VERSION.SDK) > 3) {
        	ReadText.textToSpeech(text, qa);
        }
    }


    /**
     * Play the sound indicated by the path stored on the position soundToPlayIndex of the mSoundPaths array.
     *
     * @param soundToPlayIndex
     */
    private static void playSound(int soundToPlayIndex) {
        sMediaPlayer = new MediaPlayer();
        try {
            sMediaPlayer.setDataSource(sSoundPaths.get(soundToPlayIndex));
            sMediaPlayer.setVolume(AudioManager.STREAM_MUSIC, AudioManager.STREAM_MUSIC);
            sMediaPlayer.prepare();
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    releaseSound();
                    sNumSoundsPlayed++;

                    // If there is still more sounds to play for the current card, play the next one
                    if (sNumSoundsPlayed < sSoundPaths.size()) {
                        playSound(sNumSoundsPlayed);
                    }
                }
            });

            sMediaPlayer.start();
        } catch (Exception e) {
            Log.e(AnkiDroidApp.TAG,
                    "playSounds - Error reproducing sound " + (soundToPlayIndex + 1) + " = " + e.getMessage());
            releaseSound();
        }
    }


    public static void playSound(String soundPath) {
        if (soundPath.substring(0, 3).equals("tts")) {
        	ReadText.textToSpeech(soundPath.substring(4, soundPath.length()), Integer.parseInt(soundPath.substring(3, 4)));
        } else 
        	if (sSoundPaths.contains(soundPath)) {
            sMediaPlayer = new MediaPlayer();
            try {
                sMediaPlayer.setDataSource(soundPath);
                sMediaPlayer.setVolume(AudioManager.STREAM_MUSIC, AudioManager.STREAM_MUSIC);
                sMediaPlayer.prepare();
                sMediaPlayer.start();
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "playSounds - Error reproducing sound " + soundPath + " = " + e.getMessage());
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
    }


    /**
     * Stops the playing sounds.
     */
    public static void stopSounds() {
        if (sMediaPlayer != null) {
            sMediaPlayer.stop();
            releaseSound();
        }
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 3) {
        	ReadText.stopTts();
        }
    }
}
