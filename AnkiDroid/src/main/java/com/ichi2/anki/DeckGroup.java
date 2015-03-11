package com.ichi2.anki;

import java.util.ArrayList;
import java.util.List;

/****************************************************************************************
 * Copyright (c) 2015 Allison Van Pelt <abvanpelt@gmail.com>                            *
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
public class DeckGroup {
    private List<Deck> mDecks = new ArrayList<>();

    public void add(Deck d) {
        mDecks.add(d);
    }

    public Deck get(int index) {
        return mDecks.get(index);
    }

    public int size() {
        return mDecks.size();
    }
}
