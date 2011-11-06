package com.ichi2.anki;

import java.io.File;
import java.util.HashMap;
import java.util.TreeSet;

import com.ichi2.anki.DeckPicker.AnkiFilter;
import com.ichi2.themes.StyledDialog;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.util.Log;

public class DeckManager {

	private static HashMap<String, String> mDeckPaths;
	private static String[] mDeckNames;

	public static Deck openDeck(String deckpath, int muh) {
		
		
		return null;
	}


	public static String getDeckPath(int item) {
		return getDeckPath(mDeckNames[item]);
	}
	public static String getDeckPath(String deckName) {
		return mDeckPaths.get(deckName);
	}


	public static StyledDialog getSelectDeckDialog(Context context, OnClickListener itemClickListener, OnCancelListener cancelListener, OnDismissListener dismissListener) {
		int len = 0;
		File[] fileList;

		File dir = new File(PrefSettings.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext())
				.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
		fileList = dir.listFiles(new AnkiFilter());

		if (dir.exists() && dir.isDirectory() && fileList != null) {
			len = fileList.length;
		}

		TreeSet<String> tree = new TreeSet<String>();
		mDeckPaths = new HashMap<String, String>();

		if (len > 0 && fileList != null) {
			Log.i(AnkiDroidApp.TAG,
					"DeckManager - getSelectDeckDialog, number of anki files = " + len);
			for (File file : fileList) {
				String name = file.getName().replaceAll(".anki", "");
				tree.add(name);
				mDeckPaths.put(name, file.getAbsolutePath());
			}
		}

		StyledDialog.Builder builder = new StyledDialog.Builder(context);
		builder.setTitle(R.string.fact_adder_select_deck);
		// Convert to Array
		mDeckNames = new String[tree.size()];
		tree.toArray(mDeckNames);

		builder.setItems(mDeckNames, itemClickListener);
		builder.setOnCancelListener(cancelListener);
		builder.setOnDismissListener(dismissListener);
		return builder.create();
	}
}
