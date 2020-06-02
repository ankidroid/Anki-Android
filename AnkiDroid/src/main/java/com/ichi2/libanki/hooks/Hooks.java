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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.LaTeX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

@SuppressWarnings({"PMD.AvoidReassigningParameters","PMD.AssignmentToNonFinalStatic"})
public class Hooks {
    private static Hooks sInstance;

    public static synchronized Hooks getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Hooks();
        }
        return sInstance;
    }

    private Hooks() {
    }
}

