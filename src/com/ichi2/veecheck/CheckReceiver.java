/****************************************************************************************
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

package com.ichi2.veecheck;

import android.content.Context;

import com.tomgibara.android.veecheck.VeecheckReceiver;
import com.tomgibara.android.veecheck.VeecheckSettings;
import com.tomgibara.android.veecheck.VeecheckState;
import com.tomgibara.android.veecheck.util.PrefSettings;
import com.tomgibara.android.veecheck.util.PrefState;

public class CheckReceiver extends VeecheckReceiver {

    @Override
    protected VeecheckSettings createSettings(Context context) {
        return new PrefSettings(context);
    }


    @Override
    protected VeecheckState createState(Context context) {
        return new PrefState(context);
    }

}
