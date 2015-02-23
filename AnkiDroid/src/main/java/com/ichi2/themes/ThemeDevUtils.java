package com.ichi2.themes;

import android.app.Activity;
import android.widget.Toast;

/**
 * Created by js on 3/7/15.
 */

// Temporary methods used to aid in the development of new theming

public class ThemeDevUtils {


    public static boolean volumeUp(Activity a) {
        Themes.forceDecrementTheme();  // Hack for dev/testing only.
        Themes.applyTheme(a);
//            Log.e("JS", "vol keydown, decrement");
        Toast.makeText(a,"Theme: "+Themes.getThemeName(),Toast.LENGTH_SHORT).
                show();
        a.finish();
        a.startActivity(a.getIntent() );
        return true;
    }

    public static boolean volumeDown(Activity a) {

        Themes.forceIncrementTheme();  // Hack for dev/testing only.
        Themes.applyTheme(a);
//            Log.e("JS", "keydown");
        Toast.makeText(a, "Theme: " + Themes.getThemeName(), Toast.LENGTH_SHORT).show();
        a.finish();
        a.startActivity(a.getIntent());
        return true;
    }



    //            if (! startedTracing) {
//                Log.e("JS", "volume up keydown - starting method tracing");
//                Toast.makeText(this, "start tracing ", Toast.LENGTH_SHORT).show();
//                Debug.startMethodTracing("themes");
//                startedTracing = true;
//            } else {
//                Log.e("JS", "volume up keydown - stopping method tracing");
//                Toast.makeText(this, "stop tracing ", Toast.LENGTH_SHORT).show();
//                Debug.stopMethodTracing();
//            }

}
