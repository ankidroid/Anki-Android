/****************************************************************************************
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki;

public class Consts {

    // whether new cards should be mixed with reviews, or shown first or last
    public static final int NEW_CARDS_DISTRIBUTE = 0;
    public static final int NEW_CARDS_LAST = 1;
    public static final int NEW_CARDS_FIRST = 2;

    // new card insertion order
    public static final int NEW_CARDS_RANDOM = 0;
    public static final int NEW_CARDS_DUE = 1;

    // removal types
    public static final int REM_CARD = 0;
    public static final int REM_NOTE = 1;
    public static final int REM_DECK = 2;

    // count display
    public static final int COUNT_ANSWERED = 0;
    public static final int COUNT_REMAINING = 1;

    // media log
    public static final int MEDIA_ADD = 0;
    public static final int MEDIA_REM = 1;

    // dynamic deck order
    public static final int DYN_OLDEST = 0;
    public static final int DYN_RANDOM = 1;
    public static final int DYN_SMALLINT = 2;
    public static final int DYN_BIGINT = 3;
    public static final int DYN_LAPSES = 4;
    public static final int DYN_ADDED = 5;
    public static final int DYN_DUE = 6;
    public static final int DYN_REVADDED = 7;
    public static final int DYN_DUEPRIORITY = 8;

    public static final int DYN_MAX_SIZE = 99999;

    // model types
    public static final int MODEL_STD = 0;
    public static final int MODEL_CLOZE = 1;

    public static final int STARTING_FACTOR = 2500;

    // deck schema & syncing vars
    public static final int SCHEMA_VERSION = 11;
    public static final int SYNC_ZIP_SIZE = (int)(2.5*1024*1024);
    public static final int SYNC_ZIP_COUNT = 25;
    public static final String SYNC_BASE = "https://sync.ankiweb.net/";
    public static final String SYNC_MEDIA_BASE = "https://sync.ankiweb.net/msync/";
    public static final int SYNC_VER = 9;

    public static final String HELP_SITE = "http://ankisrs.net/docs/manual.html";

    // The labels defined in consts.py are in AnkiDroid's resources files.
}
