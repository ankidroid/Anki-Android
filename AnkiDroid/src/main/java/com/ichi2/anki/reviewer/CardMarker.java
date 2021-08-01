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

package com.ichi2.anki.reviewer;

import android.view.View;
import android.widget.ImageView;

import com.ichi2.anki.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

/** Handles the star and flag marker for the card viewer */
public class CardMarker {

    public static final int FLAG_NONE = 0;
    public static final int FLAG_RED = 1;
    public static final int FLAG_ORANGE = 2;
    public static final int FLAG_GREEN = 3;
    public static final int FLAG_BLUE = 4;
    public static final int FLAG_PINK = 5;
    public static final int FLAG_TURQUOISE = 6;
    public static final int FLAG_PURPLE = 7;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FLAG_NONE, FLAG_RED, FLAG_ORANGE, FLAG_GREEN, FLAG_BLUE, FLAG_PINK, FLAG_TURQUOISE, FLAG_PURPLE})
    public @interface FlagDef {}

    @NonNull
    private final ImageView mMarkView;
    @NonNull
    private final ImageView mFlagView;

    public CardMarker(@NonNull ImageView markView, @NonNull ImageView flagView) {
        this.mMarkView = markView;
        this.mFlagView = flagView;
    }

    /** Sets the mark icon on a card (the star) */
    public void displayMark(boolean markStatus) {
        if (markStatus) {
            mMarkView.setVisibility(View.VISIBLE);
            mMarkView.setImageResource(R.drawable.ic_star_white_bordered_24dp);
        } else {
            mMarkView.setVisibility(View.INVISIBLE);
        }
    }

    /** Sets the flag icon on the card */
    public void displayFlag(@FlagDef int flagStatus) {
        switch (flagStatus) {
            case FLAG_RED:
                setFlagView(R.drawable.ic_flag_red);
                break;
            case FLAG_ORANGE:
                setFlagView(R.drawable.ic_flag_orange);
                break;
            case FLAG_GREEN:
                setFlagView(R.drawable.ic_flag_green);
                break;
            case FLAG_BLUE:
                setFlagView(R.drawable.ic_flag_blue);
                break;
            case FLAG_PINK:
                setFlagView(R.drawable.ic_flag_pink);
                break;
            case FLAG_TURQUOISE:
                setFlagView(R.drawable.ic_flag_turquoise);
                break;
            case FLAG_PURPLE:
                setFlagView(R.drawable.ic_flag_purple);
                break;
            case FLAG_NONE:
            default:
                mFlagView.setVisibility(View.INVISIBLE);
                break;
        }
    }


    private void setFlagView(@DrawableRes int drawableId) {
        //set the resource before to ensure we display the correct icon.
        mFlagView.setImageResource(drawableId);
        mFlagView.setVisibility(View.VISIBLE);
    }
}
