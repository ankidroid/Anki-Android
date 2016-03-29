/***************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2013 Jolta Technologies                                                *
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
import android.view.View;

import com.ichi2.libanki.Collection;

import timber.log.Timber;

public class Previewer extends AbstractFlashcardViewer {
    Long mCurrentCardId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        mCurrentCardId=getIntent().getLongExtra("currentCardId", -1);
        if (mCurrentCardId == -1) {
            Timber.e("Previewer started without a valid card ID");
            finishWithoutAnimation();
        }
        super.onCreate(savedInstanceState);
        showBackIcon();
        // Ensure navigation drawer can't be opened. Various actions in the drawer cause crashes.
        disableDrawerSwipe();
    }

    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        mCurrentCard = col.getCard(mCurrentCardId);
        displayCardQuestion();
        showBackIcon();
    }


    @Override
    protected void setTitle() {
        getSupportActionBar().setTitle(R.string.preview_title);
    }


    @Override
    protected void initLayout() {
        super.initLayout();
        mTopBarLayout.setVisibility(View.GONE);
    }


    // Called via mFlipCardListener in parent class when answer button pressed
    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        findViewById(R.id.answer_options_layout).setVisibility(View.GONE);
        mFlipCardLayout.setVisibility(View.GONE);
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
