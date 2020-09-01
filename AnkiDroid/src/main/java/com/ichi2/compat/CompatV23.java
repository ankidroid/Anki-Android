/***************************************************************************************
 * Copyright (c) 2017 Profpatsch <mail@profpatsch.de>                                   *
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
import android.widget.TimePicker;

/** Implementation of {@link Compat} for SDK level 23 */
@TargetApi(23)
public class CompatV23 extends CompatV21 implements Compat {

    @Override
    public void setTime(TimePicker picker, int hour, int minute) {
        picker.setHour(hour);
        picker.setMinute(minute);
    }

    @Override
    public int getHour(TimePicker picker) { return picker.getHour(); }

    @Override
    public int getMinute(TimePicker picker) { return picker.getMinute(); }

}
