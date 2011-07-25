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
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebView;

/**
 * Shows an about box, which is a small HTML page.
 */

public class About extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Themes.applyTheme(this);
        super.onCreate(savedInstanceState);
    	Resources res = getResources();

        setTitle(getAboutTitle());

        setContentView(R.layout.about);

        WebView webview = (WebView) findViewById(R.id.about);
        int background = Themes.getBackgroundColor();
        webview.setBackgroundColor(res.getColor(background == 0 ? Color.WHITE : background));        	

        String text = String.format(res.getString(R.string.about_content) + "</p></body></html>", 
                "<html><body><h2>" + res.getString(R.string.app_name) + ":<br/>", 
                "<a href=\"" + res.getString(R.string.link_anki) + "\">", 
        		"<a href=\"" + res.getString(R.string.link_issue_tracker) + "\">",
        		"<a href=\"" + res.getString(R.string.link_wiki) + "\">", 
        		"<a href=\"" + res.getString(R.string.link_forum) + "\">",
        		"<a href=\"" + res.getString(R.string.link_wikipedia_open_source) + "\">", 
        		"<a href=\"" + res.getString(R.string.link_contribution) + "\">",
        		"<a href=\"" + res.getString(R.string.link_contribution_contributors) + "\">", 
        		"<a href=\"" + res.getString(R.string.link_donation) + "\">",
        		"<a href=\"" + res.getString(R.string.licence_wiki) + "\">",
        		"<a href=\"" + res.getString(R.string.link_source) + "\">");
        webview.loadDataWithBaseURL("", text, "text/html", "utf-8", null);
    }


    private String getAboutTitle() {
        StringBuilder appName = new StringBuilder();

        appName.append("About ");
        appName.append(AnkiDroidApp.getPkgName());
        appName.append(" v");
        appName.append(AnkiDroidApp.getPkgVersion());
        return appName.toString();
    }
}
