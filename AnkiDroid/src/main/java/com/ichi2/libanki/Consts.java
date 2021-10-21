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
import net.ankiweb.rsdroid.RustCleanup;

import java.lang.annotation.Retention;

import androidx.annotation.IntDef;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class Consts {

    // whether new cards should be mixed with reviews, or shown first or last
    public static final int NEW_CARDS_DISTRIBUTE = 0;
    public static final int NEW_CARDS_LAST = 1;
    public static final int NEW_CARDS_FIRST = 2;
    @Retention(SOURCE)
    @IntDef({NEW_CARDS_DISTRIBUTE, NEW_CARDS_LAST, NEW_CARDS_FIRST})
    public @interface NEW_CARD_ORDER {}


    // new card insertion order
    public static final int NEW_CARDS_RANDOM = 0;
    public static final int NEW_CARDS_DUE = 1;
    @Retention(SOURCE)
    @IntDef({NEW_CARDS_RANDOM, NEW_CARDS_DUE})
    public @interface NEW_CARDS_INSERTION {}

    // Queue types
    public static final int QUEUE_TYPE_MANUALLY_BURIED = -3;
    public static final int QUEUE_TYPE_SIBLING_BURIED = -2;
    public static final int QUEUE_TYPE_SUSPENDED = -1;
    public static final int QUEUE_TYPE_NEW = 0;
    public static final int QUEUE_TYPE_LRN = 1;
    public static final int QUEUE_TYPE_REV = 2;
    public static final int QUEUE_TYPE_DAY_LEARN_RELEARN = 3;
    public static final int QUEUE_TYPE_PREVIEW = 4;
    @Retention(SOURCE)
    @IntDef({
            QUEUE_TYPE_MANUALLY_BURIED,
                QUEUE_TYPE_SIBLING_BURIED,
                QUEUE_TYPE_SUSPENDED,
                QUEUE_TYPE_NEW,
                QUEUE_TYPE_LRN,
                QUEUE_TYPE_REV,
                QUEUE_TYPE_DAY_LEARN_RELEARN,
                QUEUE_TYPE_PREVIEW
                })
    public @interface CARD_QUEUE {}

    // Card types
    public static final int CARD_TYPE_NEW = 0;
    public static final int CARD_TYPE_LRN = 1;
    public static final int CARD_TYPE_REV = 2;
    public static final int CARD_TYPE_RELEARNING = 3;
    @Retention(SOURCE)
    @IntDef({CARD_TYPE_NEW, CARD_TYPE_LRN, CARD_TYPE_REV, CARD_TYPE_RELEARNING})
    public @interface CARD_TYPE {}

    // removal types
    public static final int REM_CARD = 0;
    public static final int REM_NOTE = 1;
    public static final int REM_DECK = 2;
    @Retention(SOURCE)
    @IntDef({REM_CARD, REM_NOTE, REM_DECK})
    public @interface REM_TYPE {}

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
    @Retention(SOURCE)
    @IntDef({DYN_OLDEST, DYN_RANDOM, DYN_SMALLINT, DYN_BIGINT, DYN_LAPSES, DYN_ADDED, DYN_DUE, DYN_REVADDED, DYN_DUEPRIORITY})
    public @interface DYN_PRIORITY {}

    public static final int DYN_MAX_SIZE = 99999;

    // model types
    public static final int MODEL_STD = 0;
    public static final int MODEL_CLOZE = 1;
    @Retention(SOURCE)
    @IntDef({MODEL_STD, MODEL_CLOZE})
    public @interface MODEL_TYPE {}

    // deck types
    public static final int DECK_STD = 0;
    public static final int DECK_DYN = 1;
    @Retention(SOURCE)
    @IntDef({DECK_STD, DECK_DYN})
    public @interface DECK_TYPE {}

    public static final int STARTING_FACTOR = 2500;

    // deck schema & syncing vars
    public static int SCHEMA_VERSION = 11;
    /** The database schema version that we can downgrade from */
    public static final int SCHEMA_DOWNGRADE_SUPPORTED_VERSION = 16;
    public static final int SYNC_ZIP_SIZE = (int)(2.5*1024*1024);
    public static final int SYNC_ZIP_COUNT = 25;
    public static final String SYNC_BASE = "https://sync%s.ankiweb.net/";
    public static final Integer DEFAULT_HOST_NUM = null;
    /* Note: 10 if using Rust backend, 9 if using Java. Set in BackendFactory.getInstance */
    @RustCleanup("Use 10")
    public static int SYNC_VER = 9;

    // Leech actions
    public static final int LEECH_SUSPEND = 0;
    public static final int LEECH_TAGONLY = 1;

    // Buttons
    public static final int BUTTON_ONE = 1;
    public static final int BUTTON_TWO = 2;
    public static final int BUTTON_THREE = 3;
    public static final int BUTTON_FOUR = 4;
    @Retention(SOURCE)
    @IntDef({BUTTON_ONE, BUTTON_TWO, BUTTON_THREE, BUTTON_FOUR})
    public @interface BUTTON_TYPE {}

    // Revlog types
    // They are the same as Card Type except for CRAM. So one type may switch from one to other type
    public static final int REVLOG_LRN = 0;
    public static final int REVLOG_REV = 1;
    public static final int REVLOG_RELRN = 2;
    public static final int REVLOG_CRAM = 3;
    public static final int REVLOG_MANUAL = 4;
    @Retention(SOURCE)
    @IntDef({REVLOG_LRN, REVLOG_REV, REVLOG_RELRN, REVLOG_CRAM, REVLOG_MANUAL})
    public @interface REVLOG_TYPE {}

    // The labels defined in consts.py are in AnkiDroid's resources files.

    public static final long DEFAULT_DECK_ID = 1;
    /** Default dconf - can't be removed */
    public static final long DEFAULT_DECK_CONFIG_ID = 1;

    public static final String FIELD_SEPARATOR = Character.toString('\u001f');
}
