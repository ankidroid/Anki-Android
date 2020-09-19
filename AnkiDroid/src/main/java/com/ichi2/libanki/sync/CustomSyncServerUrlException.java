/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.libanki.sync;

import static com.ichi2.libanki.sync.Syncer.ConnectionResultType.CUSTOM_SYNC_SERVER_URL;

public class CustomSyncServerUrlException extends RuntimeException {
    private final String mUrl;


    public CustomSyncServerUrlException(String url, IllegalArgumentException ex) {
        super(getMessage(url), ex);
        this.mUrl = url;
    }


    private static String getMessage(String url) {
        return "Invalid Custom Sync Server URL: " + url;
    }


    @Override
    public String getLocalizedMessage() {
        // Janky. Connection uses this as a string to return, which is switched on to determine the message in DeckPicker
        return CUSTOM_SYNC_SERVER_URL.toString();
    }


    public String getUrl() {
        return mUrl;
    }
}
