/****************************************************************************************
 * Copyright (c) 2011 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.compat;

import android.annotation.TargetApi;
import android.support.v4.app.FragmentActivity;
import android.webkit.WebView;

/** Implementation of {@link Compat} for SDK level 5 */
@TargetApi(5)
public class CompatV5 extends CompatV4 implements Compat {
    @Override
    public void onAttachedToWindow(FragmentActivity fragment) {
        fragment.onAttachedToWindow();
    }
    @Override
    public void setScrollbarFadingEnabled(WebView webview, boolean enable) {
        webview.setScrollbarFadingEnabled(enable);
    }
}
