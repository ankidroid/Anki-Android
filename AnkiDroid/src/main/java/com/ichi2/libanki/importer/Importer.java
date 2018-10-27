/***************************************************************************************
 * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.libanki.importer;


import android.content.Context;
import android.content.res.Resources;

import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Utils;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"PMD.MethodNamingConventions"})
public abstract class Importer {

    protected boolean mNeedMapper = false;
    protected boolean mNeedDelimiter = false;
    protected String mFile;
    protected List<String> mLog;
    protected Collection mCol;
    protected int mTotal;

    private long mTs;
    protected Collection mDst;
    protected Collection mSrc;

    protected Context mContext;
    protected DeckTask.ProgressCallback mProgress;

    public Importer(Collection col, String file) {
        mFile = file;
        mLog = new ArrayList<>();
        mCol = col;
        mTotal = 0;
        mContext = col.getContext();
    }

    abstract public void run();

    /**
     * Timestamps
     * ***********************************************************
     * It's too inefficient to check for existing ids on every object,
     * and a previous import may have created timestamps in the future, so we
     * need to make sure our starting point is safe.
     */

    protected void _prepareTS() {
        mTs = Utils.maxID(mDst.getDb());
    }


    protected long ts() {
        mTs++;
        return mTs;
    }


    /**
     * The methods below are not in LibAnki.
     * ***********************************************************
     */

    public void setProgressCallback(DeckTask.ProgressCallback progressCallback) {
        mProgress = progressCallback;
    }


    protected Resources getRes() {
        return mContext.getResources();
    }

    public List<String> getLog() {
        return mLog;
    }
}
