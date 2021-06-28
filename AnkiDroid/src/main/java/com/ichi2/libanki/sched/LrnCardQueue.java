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

import java.util.Collections;

class LrnCardQueue extends CardQueue<LrnCard> {
    /**
     * Whether the queue already contains its current expected value.
     * If it's not the case, then we won't add cards reviewed immediately and wait for a filling to occur.
     */
    private boolean mIsFilled = false;

    public LrnCardQueue(AbstractSched sched) {
        super(sched);
    }

    public void add(long due, long cid) {
        add(new LrnCard(getCol(), due, cid));
    }

    public void sort() {
        Collections.sort(getQueue());
    }

    public long getFirstDue() {
        return getQueue().getFirst().getDue();
    }


    @Override
    public void clear() {
        super.clear();
        mIsFilled = false;
    }

    public void setFilled() {
        mIsFilled = true;
    }

    public boolean isFilled() {
        return mIsFilled;
    }
}
