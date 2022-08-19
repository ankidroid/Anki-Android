/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.sched;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

class LrnCard extends Card.Cache implements Comparable<LrnCard> {
    private final long mDue;
    public LrnCard(Collection col, long due, long cid) {
        super(col, cid);
        mDue = due;
    }

    public long getDue () {
        return mDue;
    }

    @Override
    public int compareTo(LrnCard o) {
        return Long.compare(mDue, o.mDue);
    }
}
