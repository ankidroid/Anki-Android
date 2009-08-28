package com.ichi2.anki;

import android.app.Activity;
import android.database.SQLException;
import android.os.Bundle;
import android.webkit.WebView;

public class About extends Activity {
	
	public void onCreate(Bundle savedInstanceState) throws SQLException {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		WebView webview = (WebView) findViewById(R.id.about);
		webview.loadUrl("file:///android_asset/about.html");
	}
}