/***************************************************************************************
 * Copyright (c) 2016 rubyu <ruby.u.g@gmail.com>                                        *
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
import android.webkit.WebSettings;

/** Implementation of {@link Compat} for SDK level 17 */
@TargetApi(17)
public class CompatV17 extends CompatV16 implements Compat {

    @Override
    public void setHTML5MediaAutoPlay(WebSettings webSettings, Boolean allow) {
        webSettings.setMediaPlaybackRequiresUserGesture(!allow);
    }
}
