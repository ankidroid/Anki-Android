package com.ichi2.anki;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

public class DeckProperties extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	static final String TAG = "AnkiDroid";

	public class DeckPreferenceHack implements SharedPreferences
	{

		protected Map<String, String> values = new HashMap<String, String>();

		public DeckPreferenceHack()
		{
			this.cacheValues();

		}

		protected void cacheValues()
		{
			Log.i(TAG, "DeckPreferences - CacheValues");
			String syncName = String.valueOf(AnkiDroidApp.deck().getSyncName());
			if(!"".equalsIgnoreCase(syncName) && !"null".equalsIgnoreCase(syncName))
			{
				values.put("isSyncOn", "true");
				values.put("syncName", String.valueOf(AnkiDroidApp.deck().getSyncName()));
			}
			else
			{
				values.put("isSyncOn", "false");
				values.put("syncName", "");
			}
		}

		public class Editor implements SharedPreferences.Editor
		{

			public ContentValues update = new ContentValues();

			public SharedPreferences.Editor clear()
			{
				Log.d( TAG, "clear()" );
				update = new ContentValues();
				return this;
			}

			public boolean commit()
			{
				Log.d( TAG, "DeckPreferences - commit() changes back to database" );

				// make sure we refresh the parent cached values
				// cacheValues();

				for ( Entry<String, Object> entry : update.valueSet() )
				{
					if(entry.getKey().equals("syncName"))
					{
						AnkiDroidApp.deck().setSyncName(entry.getValue().toString());
					}
					else if(entry.getKey().equals("isSyncOn"))
					{
						if("false".equalsIgnoreCase(entry.getValue().toString()))
						{
							AnkiDroidApp.deck().setSyncName("");
						}
					}
				}
				// make sure we refresh the parent cached values
				cacheValues();

				// and update any listeners
				for ( OnSharedPreferenceChangeListener listener : listeners )
				{
					listener.onSharedPreferenceChanged( DeckPreferenceHack.this, null );
				}

				return true;
			}

			public android.content.SharedPreferences.Editor putBoolean( String key, boolean value )
			{
				return this.putString( key, Boolean.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putFloat( String key, float value )
			{
				return this.putString( key, Float.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putInt( String key, int value )
			{
				return this.putString( key, Integer.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putLong( String key, long value )
			{
				return this.putString( key, Long.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putString( String key, String value )
			{
				Log.d( this.getClass().toString(), String.format("Editor.putString(key=%s, value=%s)", key, value ) );
				update.put( key, value );
				return this;
			}

			public android.content.SharedPreferences.Editor remove( String key )
			{
				Log.d( this.getClass().toString(), String.format( "Editor.remove(key=%s)", key ) );
				update.remove( key );
				return this;
			}

		}

		public boolean contains( String key )
		{
			return values.containsKey(key);
		}

		public Editor edit()
		{
			return new Editor();
		}

		public Map<String, ?> getAll()
		{
			return values;
		}

		public boolean getBoolean( String key, boolean defValue )
		{
			return Boolean.valueOf( this.getString( key, Boolean.toString( defValue ) ) );
		}

		public float getFloat( String key, float defValue )
		{
			return Float.valueOf( this.getString( key, Float.toString( defValue ) ) );
		}

		public int getInt( String key, int defValue )
		{
			return Integer.valueOf( this.getString( key, Integer.toString( defValue ) ) );
		}

		public long getLong( String key, long defValue )
		{
			return Long.valueOf( this.getString( key, Long.toString( defValue ) ) );
		}

		public String getString( String key, String defValue )
		{
			Log.d( this.getClass().toString(), String.format( "getString(key=%s, defValue=%s)", key, defValue ) );

			if ( !values.containsKey( key ) )
				return defValue;
			return values.get( key );
		}

		public List<OnSharedPreferenceChangeListener> listeners = new LinkedList<OnSharedPreferenceChangeListener>();

		public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
		{
			listeners.add( listener );
		}

		public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
		{
			listeners.remove( listener );
		}

	}

	protected DeckPreferenceHack pref;

	@Override
	public SharedPreferences getSharedPreferences( String name, int mode )
	{
		Log.d( this.getClass().toString(), String.format( "getSharedPreferences(name=%s)", name ) );
		return this.pref;
	}

	@Override
	public void onCreate( Bundle icicle )
	{
		super.onCreate( icicle );

		if ( AnkiDroidApp.deck() == null )
		{
			Log.i( TAG, "DeckPreferences - Selected Deck is NULL" );
			finish();
		}
		else
		{
			this.pref = new DeckPreferenceHack();
			this.pref.registerOnSharedPreferenceChangeListener( this );

			this.addPreferencesFromResource( R.layout.deck_properties );
			this.updateSummaries();
		}
	}

	public void onSharedPreferenceChanged( SharedPreferences sharedPreferences, String key )
	{
		// update values on changed preference
		this.updateSummaries();
	}

	protected void updateSummaries()
	{
		// for all text preferences, set summary as current database value
		for ( String key : this.pref.values.keySet() )
		{
			Preference pref = this.findPreference( key );
			if ( pref == null )
				continue;
			if ( pref instanceof CheckBoxPreference )
				continue;
			pref.setSummary( this.pref.getString( key, "" ) );
		}
	}
}
