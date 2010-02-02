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

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.Log;

/**
 * Class used to handle, load and play sound files on Ankidroid.
 */
public class Sound {

	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "AnkiDroid";
	/**
	 * Pattern used to identify the markers for sound files
	 */
	private static Pattern pattern = Pattern.compile("\\[sound\\:([^\\[\\]]*)\\]");
	/**
	 * ArrayList to store the current sound files
	 */
	private static ArrayList<MediaPlayer> sounds;
	
	/**
	 * Searches and loads the sound files specified on content (belonging to deck deckFilename) and cleans the markers used for it
	 * @param deckFilename Deck's filename whose sound files we are loading
	 * @param content HTML content of a card's side (question or answer)
	 * @return content Content without the markers of sounds, ready to be displayed
	 */
	public static String extractSounds(String deckFilename, String content)
	{
		Log.i(TAG, "getSounds");
		sounds = new ArrayList<MediaPlayer>();
		Matcher matcher = pattern.matcher(content);
		while(matcher.find())
		{
			String contentToReplace = matcher.group();
			content = content.replace(contentToReplace, "");
			String sound = matcher.group(1);
			Log.i(TAG, "Sound " + matcher.groupCount() + ": " + sound);
			MediaPlayer soundPlayer = new MediaPlayer();
			String soundPath = deckFilename.replaceAll(".anki", "") + ".media/" + sound;
			Log.i(TAG, "getSounds - soundPath = " + soundPath);
			try 
			{
				soundPlayer.setDataSource(soundPath);
				soundPlayer.setVolume(AudioManager.STREAM_MUSIC, AudioManager.STREAM_MUSIC);
				soundPlayer.prepare();
				sounds.add(soundPlayer);

				if(sounds.size() > 1)
				{
					sounds.get(sounds.size() - 2).setOnCompletionListener(new OnCompletionListener() {

						public void onCompletion(MediaPlayer mp) {
							try
							{
								int i = sounds.indexOf(mp) + 1;
								sounds.get(i).start();
							} catch (Exception e)
							{
								Log.e(TAG, "playSounds - Error reproducing a sound = " + e.getMessage());
							}
						}
				
					});
				}
			} catch (Exception e)
			{
				Log.e(TAG, "getSounds - Error setting data source for Media Player = " + e.getMessage());
			}

		}
		return content;
	}
	
	/**
	 * Play the sounds that were previously extracted from a side of a card
	 */
	public static void playSounds()
	{
		if(!sounds.isEmpty())
		{
			sounds.get(0).start();
		}
	}
}
