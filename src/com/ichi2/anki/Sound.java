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
     * Tag for logging messages
     */
    private static final String TAG = "AnkiDroid";

    /**
     * Pattern used to identify the markers for sound files
     */
    private static Pattern mSoundPattern = Pattern.compile("\\[sound\\:([^\\[\\]]*)\\]");

    /**
     * Media player used to play the sounds
     */
    private static MediaPlayer mMediaPlayer;

    /**
     * ArrayList to store the current sound paths
     */
    private static ArrayList<String> mSoundPaths;

    /**
     * Counter of the number of sounds played out of the total number of sounds in soundPaths
     */
    private static int numSoundsPlayed;

    /**
     * Variables used to track the total time spent
     */
    private static long mStartTime, mFinishTime;

    /**
     * Variables used to track the time spent playing one particular sound
     */
    private static long mStartSoundTime, mFinishSoundTime;


    public static String parseSounds(String deckFilename, String content) {
        mStartTime = System.currentTimeMillis();

        StringBuilder stringBuilder = new StringBuilder();
        String contentLeft = content;

        Log.i(TAG, "parseSounds");
        mSoundPaths = new ArrayList<String>();
        Matcher matcher = mSoundPattern.matcher(content);
        // While there is matches of the pattern for sound markers
        while (matcher.find()) {
            // Get the sound file name
            String sound = matcher.group(1);
            // Log.i(TAG, "Sound " + matcher.groupCount() + ": " + sound);

            // Construct the sound path and store it
            String soundPath = deckFilename.replace(".anki", ".media/") + sound;
            // Log.i(TAG, "parseSounds - soundPath = " + soundPath);
            mSoundPaths.add(soundPath);

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
            // Log.i(TAG, "Content left = " + contentLeft);
        }

        stringBuilder.append(contentLeft);

        mFinishTime = System.currentTimeMillis();
        Log.i(TAG, mSoundPaths.size() + " sounds parsed in " + (mFinishTime - mStartTime) + " milliseconds");

        return stringBuilder.toString();
    }


    /**
     * Plays the sounds stored on the paths indicated by mSoundPaths
     */
    public static void playSounds() {
        // If there are sounds to play for the current card, play the first one
        if (mSoundPaths != null && mSoundPaths.size() > 0) {
            numSoundsPlayed = 0;
            mStartTime = System.currentTimeMillis();
            playSound(numSoundsPlayed);
        }
    }


    /**
     * Play the sound indicated by the path stored on the position soundToPlayIndex of the mSoundPaths array
     * 
     * @param soundToPlayIndex
     */
    private static void playSound(int soundToPlayIndex) {
        mStartSoundTime = System.currentTimeMillis();
        mMediaPlayer = new MediaPlayer();
        try {
            mMediaPlayer.setDataSource(mSoundPaths.get(soundToPlayIndex));
            mMediaPlayer.setVolume(AudioManager.STREAM_MUSIC, AudioManager.STREAM_MUSIC);
            mMediaPlayer.prepare();
            mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    releaseSound();
                    numSoundsPlayed++;

                    mFinishSoundTime = System.currentTimeMillis();
                    // Log.i(TAG, "Sound " + numSoundsPlayed + " played in " + (mFinishSoundTime - mStartSoundTime) +
                    // " milliseconds");

                    // If there is still more sounds to play for the current card, play the next one
                    if (numSoundsPlayed < mSoundPaths.size()) {
                        playSound(numSoundsPlayed);
                    } else {
                        // If it was the last sound, annotate the total time taken
                        mFinishTime = System.currentTimeMillis();
                        // Log.i(TAG, numSoundsPlayed + " sounds played in " + (mFinishTime - mStartTime) +
                        // " milliseconds");
                    }
                }
            });

            mMediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "playSounds - Error reproducing sound " + (soundToPlayIndex + 1) + " = " + e.getMessage());
            releaseSound();
        }
    }


    public static void playSound(String soundPath) {
        if (mSoundPaths.contains(soundPath)) {
            mStartSoundTime = System.currentTimeMillis();
            mMediaPlayer = new MediaPlayer();
            try {
                mMediaPlayer.setDataSource(soundPath);
                mMediaPlayer.setVolume(AudioManager.STREAM_MUSIC, AudioManager.STREAM_MUSIC);
                mMediaPlayer.prepare();
                mMediaPlayer.start();
            } catch (Exception e) {
                Log.e(TAG, "playSounds - Error reproducing sound " + soundPath + " = " + e.getMessage());
                releaseSound();
            }
            mFinishSoundTime = System.currentTimeMillis();
            Log.i(TAG, "Sound " + soundPath + " played in " + (mFinishSoundTime - mStartSoundTime) + " milliseconds");
        }
    }


    /**
     * Releases the sound
     */
    private static void releaseSound() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }


    /**
     * Stops the playing sounds
     */
    public static void stopSounds() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            releaseSound();
        }
    }
}
