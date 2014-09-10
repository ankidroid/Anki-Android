package com.ichi2.anki;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Class which handles how the application responds to different intents, forcing it to always be single task,
 * but allowing custom behavior depending on the intent
 * 
 * @author Tim
 *
 */

public class IntentHandler extends Activity {
    @Override 
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Intent reloadIntent = new Intent(this, DeckPicker.class);
        if (intent.getExtras() != null) {
            reloadIntent.putExtras(intent.getExtras());
        }
        if (intent.getData() != null) {
            reloadIntent.setData(intent.getData());
        }
        String action = intent.getAction();
        switch (action) {
            case Intent.ACTION_VIEW:
                // This intent is used for opening apkg package
                // We want to go immediately to DeckPicker, clearing any history in the process
                // TODO: Still one bug, where if AnkiDroid is launched via ACTION_VIEW, 
                // then subsequent ACTION_VIEW events bypass IntentHandler. Prob need to do something onResume() of AnkiActivity
                reloadIntent.setAction(action);
                reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(reloadIntent);
                finish();
                break;
            default :
                // Launcher intents should start DeckPicker if no other task exists,
                // otherwise go to previous task
                reloadIntent.setAction(Intent.ACTION_MAIN);
                reloadIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                reloadIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityIfNeeded(reloadIntent, 0);
                finish();
                break;
        }
    }  
}