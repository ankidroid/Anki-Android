/*
 * Copying and distribution of this file, with or without modification, are permitted in any
 * medium without royalty. This file is offered as-is, without any warranty.
 */
package com.ichi2.anki;

import android.net.Uri;

/**
 * <p>
 *     The contract between AnkiDroid and applications for user statistics.
 *     Contains definitions for the supported URIs and columns
 * </p>
 * <p>
 *     Card stats are relatively simple. No modifications (create, update, insert, delete)
 *     are allowed, the only valid operation is a read, and it will return the current card count
 *     by status for the collection as a whole. Stats information is accessed the following way:
 * </p>
 * <ul>
 *     <li>
 *         <pre>
 *             <code>
 *         // Query collection counts
 *         final Cursor cursor = cr.query(StatsContract.CONTENT_URI + "/cardcount", null, null, null, null);
 *             </code>
 *         </pre>
 *     </li>
 *     <li>
 *         There will be one row returned, and it will have the number of mature, young,
 *         new and suspended cards in the collection, in that order.
 *     </li>
 *     <li>
 *         Future additions could allow for card counts by deck, card counts by period etc. PRs welcome.
 *     </li>
 * </ul>
 */
public class StatsContract {
    public static final String AUTHORITY = "com.ichi2.anki.stats";

    /** A content:// style uri to the authority for the stats provider */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /* Don't create instances of this class */
    private StatsContract() {
        // don't instantiate
    }


    public static class CardCount {

        /** The content:// style URI path segment for stats */
        public static final String CONTENT_URI_PATH = "cardcount";

        /** MIME type used for Anki stats */
        public static final String CONTENT_TYPE = "vnd.android.cursor.item/vnd.com.ichi2.anki.cardcount";
    }
}
