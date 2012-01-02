/***************************************************************************************
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
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

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.libanki.Utils;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Create a new deck, with zero cards.
 * Just a basic question/answer model, no choice for now.
 * We just copy empty.anki to the decks directory under the specified name.
 */

public class DeckCreator extends Activity {

    public final static String EMPTY_DECK_NAME = "empty.anki";
    
    private String mPrefDeckPath;
    
    private Button mCreate;
    private Button mCancel;
    private EditText mFilename;
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
    	Resources res = getResources();

        setTitle(res.getString(R.string.menu_create_deck));

        // Get decks path
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefDeckPath = preferences.getString("deckPath", AnkiDroidApp.getStorageDirectory());
        
        View mainView = getLayoutInflater().inflate(R.layout.deck_creator, null);
        setContentView(mainView);
        Themes.setWallpaper(mainView);
        
        mCreate = (Button) findViewById(R.id.DeckCreatorOKButton);
        mCancel = (Button) findViewById(R.id.DeckCreatorCancelButton);
        mFilename = (EditText) findViewById(R.id.DeckCreatorFilename);

        // When "OK" is clicked.
        mCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String filename = mFilename.getText().toString();
                if (!filename.equals("")) {
                    if (createDeck(filename) == true) {
                        setResult(RESULT_OK);
                    }
                }
                closeDeckCreator();
            }
        });
        
        // When "Cancel" is clicked.
        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                closeDeckCreator();
            }
        });
    }


    /**
     * Create a new deck with given name
     * @param filename for instance "my Japanese words";
     * @return success or not
     */
    private boolean createDeck(String filename) {
        Log.d(AnkiDroidApp.TAG, "Creating deck: " + filename);

        filename = filename + ".anki";

        // If decks directory does not exist, create it.
        File decksDirectory = new File(mPrefDeckPath);
        AnkiDroidApp.createDecksDirectoryIfMissing(decksDirectory);

        File destinationFile = new File(mPrefDeckPath, filename);
        if (destinationFile.exists()) {
            return false;
        }
        
        try {
            // Copy the empty deck from the assets to the SD card.
            InputStream stream = getResources().getAssets().open(EMPTY_DECK_NAME);
            Utils.writeToFile(stream, destinationFile.getAbsolutePath());
            stream.close();
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, Log.getStackTraceString(e));
            Log.e(AnkiDroidApp.TAG, "onCreate - The copy of empty.anki to the SD card failed.");
            return false;
        }

//        Deck.initializeEmptyDeck(mPrefDeckPath + "/" + filename);

        return true;
    }
    
    
    private void closeDeckCreator() {
        finish();
        if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
            ActivityTransitionAnimation.slide(DeckCreator.this, ActivityTransitionAnimation.LEFT);
        }    
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "DeckCreator - onBackPressed()");
            closeDeckCreator();
            MetaDB.closeDB();
        }

        return super.onKeyDown(keyCode, event);
    }
}
