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

import com.ichi2.themes.Themes;

import android.app.Activity;
import android.content.res.Resources;
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
        webview.setBackgroundColor(res.getColor(Themes.getBackgroundColor()));        	

	String[] content = res.getStringArray(R.array.about_content);
	StringBuilder sb = new StringBuilder();
	sb.append("<html><body>");
	sb.append(String.format(content[0], res.getString(R.string.app_name), res.getString(R.string.link_anki))).append("<br/><br/>");
	sb.append(String.format(content[1], res.getString(R.string.link_issue_tracker), res.getString(R.string.link_wiki), res.getString(R.string.link_forum))).append("<br/><br/>");
	sb.append(String.format(content[2], res.getString(R.string.link_wikipedia_open_source), res.getString(R.string.link_contribution), res.getString(R.string.link_contribution_contributors))).append(" ");
	sb.append(String.format(content[3], res.getString(R.string.link_translation), res.getString(R.string.link_donation))).append("<br/><br/>");
	sb.append(String.format(content[4], res.getString(R.string.licence_wiki), res.getString(R.string.link_source))).append("<br/><br/>");
	sb.append("</body></html>");
        webview.loadDataWithBaseURL("", sb.toString(), "text/html", "utf-8", null);
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
