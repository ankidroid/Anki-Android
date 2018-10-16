/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2018 Marcin Moskała <marcinmoskala@gmail.com>                          *
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

package com.ichi2.anki.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.StatsContract;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.requery.android.database.sqlite.SQLiteDatabase;
import timber.log.Timber;

/**
 * Supported URIs:
 * .../cardcount (number of different types of cards respectively mature, young, new and suspended)
 * <p/>
 **/
public class StatsProvider extends ContentProvider {

    private Context mContext;

    private static final UriMatcher uriMatcher = buildUriMatcher();

    private static final int CARD_TYPES_COUNT = 100;

    private Collection mCollection;

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        int match = uriMatcher.match(uri);
        switch (match) {
            case CARD_TYPES_COUNT:
                return StatsContract.CardCount.CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI : " + uri);
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!ProviderUtils.hasReadWritePermission(mContext) &&
                ProviderUtils.shouldEnforceQueryOrInsertSecurity(this, mContext)) {
            ProviderUtils.throwSecurityException(this, mContext, "query", uri);
        }
        try {
            switch (uriMatcher.match(uri)) {
                case CARD_TYPES_COUNT:
                    return makeCardsTypesCursor();
                default:
                    return null;
            }
        } catch (Throwable error) {
            Timber.e(error, "Failed to query StatsProvider");
        }
        return null;
    }

    // Cannot insert stats
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    // Cannot delete stats
    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    // Cannot modify stats
    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public boolean onCreate() {
        mContext = getContext();
        mCollection = CollectionHelper.getInstance().getColSafe(mContext);
        return true;
    }

    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(StatsContract.AUTHORITY, StatsContract.CardCount.CONTENT_URI_PATH, CARD_TYPES_COUNT);
        return matcher;
    }

    // Cursor contains counts for all cards that are mature, young, new and suspended.
    private Cursor makeCardsTypesCursor() {
        SQLiteDatabase db = getDbOrNull();
        if (db == null) {
            initCollection();
            db = getDbOrNull();
        }
        if (db == null) {
            return null;
        }
        return Stats.getCardTypesStats(db);
    }

    private void initCollection() {
        mCollection = CollectionHelper.getInstance().getColSafe(getContext());
    }

    private @Nullable SQLiteDatabase getDbOrNull() {
        Collection col = mCollection;
        if(col == null) {
            return null;
        }
        DB db = mCollection.getDb();
        if(db == null) {
            return null;
        }
        return db.getDatabase();
    }
}