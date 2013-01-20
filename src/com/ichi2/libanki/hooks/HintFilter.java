/***************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

package com.ichi2.libanki.hooks;

import android.content.res.Resources;

import com.ichi2.anki.R;

import java.util.Locale;

public class HintFilter {
    public void install(Hooks h) {
        h.addHook("fmod_hint", new Hint());
    }
    
    public class Hint extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            String txt = (String) arg;
            if (txt.trim().length() == 0) {
                return "";
            }
            Resources res = (Resources) args[1];
            // random id
            String domid = "hint" + txt.hashCode();
            return "<a class=hint href=\"#\" onclick=\"this.style.display='none';document.getElementById('" +
                    domid + "').style.display='block';return false;\">" +
                    res.getString(R.string.show_hint) + "</a><div id=\"" +
            		domid + "\" class=hint style=\"display: none\">" + txt + "</div>";
        }
    }
}
