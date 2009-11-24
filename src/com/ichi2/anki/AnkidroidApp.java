package com.ichi2.anki;

import android.app.Application;


public class AnkidroidApp extends Application
{
    private Deck loadedDeck;
    
    public static Deck deck()
    {
        return instance.loadedDeck;
    }
    
    public static void setDeck( Deck deck )
    {
        instance.loadedDeck = deck;
    }
    
    private static AnkidroidApp instance;
    
    public static AnkidroidApp getInstance()
    {
        return instance;
    }
    
    @Override
    public void onCreate()
    {
        super.onCreate();
        instance = this;
        
    }

}
