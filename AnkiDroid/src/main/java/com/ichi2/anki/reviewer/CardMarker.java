package com.ichi2.anki.reviewer;

import android.view.View;
import android.widget.ImageView;

import com.ichi2.anki.R;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

/** Handles the star and flag marker for the card viewer */
public class CardMarker {

    public static final int FLAG_NONE = 0;
    public static final int FLAG_RED = 1;
    public static final int FLAG_ORANGE = 2;
    public static final int FLAG_GREEN = 3;
    public static final int FLAG_BLUE = 4;

    @NonNull
    private final ImageView markView;
    @NonNull
    private final ImageView flagView;

    public CardMarker(@NonNull ImageView markView, @NonNull ImageView flagView) {
        this.markView = markView;
        this.flagView = flagView;
    }

    /** Sets the mark icon on a card (the star) */
    public void displayMark(boolean markStatus) {
        if (markStatus) {
            markView.setVisibility(View.VISIBLE);
            markView.setImageResource(R.drawable.ic_star_white_bordered_24dp);
        } else {
            markView.setVisibility(View.INVISIBLE);
        }
    }

    /** Sets the flag icon on the card */
    public void displayFlag(int flagStatus) {
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
            case FLAG_NONE:
            default:
                flagView.setVisibility(View.INVISIBLE);
                break;
        }
    }


    private void setFlagView(@DrawableRes int drawableId) {
        //set the resource before to ensure we display the correct icon.
        flagView.setImageResource(drawableId);
        flagView.setVisibility(View.VISIBLE);
    }
}
