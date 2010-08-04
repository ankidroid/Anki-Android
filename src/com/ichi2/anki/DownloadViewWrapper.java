package com.ichi2.anki;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DownloadViewWrapper {
	
	private View base;
	private TextView headerTitle = null;
	private TextView downloadTitle = null;
	private ProgressBar progressBar = null;
	private TextView progressBarText = null;
	private TextView deckTitle = null;
	private TextView deckFacts = null;
	
	DownloadViewWrapper(View base) {
		this.base = base;
	}
	
	TextView getHeaderTitle() 
	{
		if(headerTitle == null)
		{
			headerTitle = (TextView) base.findViewById(R.id.header_title);
		}
		return headerTitle;
	}
	
	TextView getDownloadTitle() 
	{
		if(downloadTitle == null)
		{
			downloadTitle = (TextView) base.findViewById(R.id.download_title);
		}
		return downloadTitle;
	}
	
	ProgressBar getProgressBar()
	{
		if(progressBar == null)
		{
			progressBar = (ProgressBar) base.findViewById(R.id.progress_bar);
		}
		return progressBar;
	}
	
	TextView getProgressBarText()
	{
		if(progressBarText == null)
		{
			progressBarText = (TextView) base.findViewById(R.id.progress_text);
		}
		return progressBarText;
	}

	TextView getDeckTitle()
	{
		if(deckTitle == null)
		{
			deckTitle = (TextView) base.findViewById(R.id.deck_title);
		}
		return deckTitle;
	}
	
	TextView getDeckFacts()
	{
		if(deckFacts == null)
		{
			deckFacts = (TextView) base.findViewById(R.id.deck_facts);
		}
		return deckFacts;
	}
}
