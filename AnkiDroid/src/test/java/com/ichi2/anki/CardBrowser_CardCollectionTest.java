/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import org.junit.Test;

import java.util.Arrays;


import androidx.annotation.NonNull;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CardBrowser_CardCollectionTest {

    @Test
    public void reverseFixesPosition() {
        CardBrowser.CardCollection<Positioned> cardCollection = createCollection(new Positioned(0), new Positioned(1));

        assertThat(cardCollection.get(0).getPosition(), is(0));
        assertThat(cardCollection.get(0).getInitialValue(), is(0));

        cardCollection.reverse();

        assertThat(cardCollection.get(0).getPosition(), is(0));
        assertThat(cardCollection.get(0).getInitialValue(), is(1));
    }


    @NonNull
    protected CardBrowser.CardCollection<Positioned> createCollection(Positioned... toInsert) {
        CardBrowser.CardCollection<Positioned> cardCollection = new CardBrowser.CardCollection<>();
        cardCollection.replaceWith(Arrays.asList(toInsert));
        return cardCollection;
    }


    private static class Positioned implements CardBrowser.PositionAware {

        private int mPosition;
        private int mInitialValue;

        public Positioned(int position) {
            mPosition = position;
            mInitialValue = position;
        }

        private int getInitialValue() {
            return mInitialValue;
        }

        @Override
        public int getPosition() {
            return mPosition;
        }


        @Override
        public void setPosition(int value) {
            mPosition = value;
        }
    }
}
