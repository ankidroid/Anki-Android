/***************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Jolta Technologies												*
 * Copyright (c) 2014 Bruno Romero de Azevedo <brunodea@inf.ufsm.br>                    *
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

package com.ichi2.anki;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import com.ichi2.libanki.Collection;

public class Previewer extends AbstractFlashcardViewer {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(AnkiDroidApp.TAG, "PreviewClass - onCreate");
    }


    @Override
    protected void initActivity(Collection col) {
        super.initActivity(col);

        mCurrentCard = CardEditor.mCurrentEditedCard;

        Previewer.this.displayCardQuestion();
        Previewer.this.displayCardAnswer();

        hideEaseButtons();
    }


    @Override
    protected void setTitle() {
        AnkiDroidApp.getCompat().setTitle(this, getResources().getString(R.string.preview_title), mInvertedColors);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }


    @Override
    protected void initLayout() {
        super.initLayout();
        mDrawerToggle.setDrawerIndicatorEnabled(false);
        findViewById(R.id.answer_options_layout).setVisibility(View.GONE);
        mTopBarLayout.setVisibility(View.GONE);
        mFlipCardLayout.setVisibility(View.GONE);
    }


    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        hideEaseButtons();
    }


    // we don't want the Activity title to be changed.
    @Override
    protected void updateScreenCounts() {
    }


    // No Gestures!
    @Override
    protected void executeCommand(int which) {
    }
}
