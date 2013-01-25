package com.ichi2.utils;

import java.io.File;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.Collection;

public class DiskUtil
{

    public static File getStoringDirectory()
    {
        
        Collection col = AnkiDroidApp.getCol();
        String mediaDir = col.getMedia().getDir() + "/";
        
        File mediaDirFile = new File(mediaDir);
        
        return mediaDirFile;
    }

}
