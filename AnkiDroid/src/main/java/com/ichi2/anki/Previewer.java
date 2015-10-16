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

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class Previewer extends AbstractFlashcardViewer {
    Long mCurrentCardId;
    Long mCurrentModelId;
    int mOrd;
    private final String DUMMY_TAG = "DUMMY_NOTE_TO_DELETE_x0-90-fa";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        mCurrentCardId=getIntent().getLongExtra("currentCardId", -1);
        mCurrentModelId=getIntent().getLongExtra("modelId", -1);
        mOrd=getIntent().getIntExtra("cardOrd", -1);
        if (mCurrentCardId == -1 && (mCurrentModelId == -1 || mOrd == -1)) {
            Timber.e("Previewer started without a valid card ID or model");
            finishWithoutAnimation();
        }
        super.onCreate(savedInstanceState);
        showBackIcon();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCurrentCardId == -1L) {
            Timber.d("Deleting dummy card(s)");
            deleteDummyCards();
        }
    }

    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);
        if (mCurrentCardId != -1L) {
            // Use provided card id if given
            mCurrentCard = col.getCard(mCurrentCardId);
        } else {
            // Otherwise create a dummy note
            mCurrentCard = getDummyCard();
        }
        displayCardQuestion();
        showBackIcon();
    }

    /**
     * Get a dummy card
     * @return
     */
    private Card getDummyCard() {
        Timber.d("Creating dummy note");
        JSONObject model = getCol().getModels().get(mCurrentModelId);
        Note n =getCol().newNote(model);
        ArrayList<String> fieldNames = getCol().getModels().fieldNames(model);
        for (int i = 0; i < fieldNames.size(); i++) {
            n.setField(i, fieldNames.get(i));
        }
        n.addTag(DUMMY_TAG);
        getCol().addNote(n);
        return getCol().getCard(n.cards().get(mOrd).getId());
    }

    private void deleteDummyCards() {
        // TODO: make into an async task
        List<Long> remnantNotes = getCol().findNotes("tag:" + DUMMY_TAG);
        if (remnantNotes.size() > 0) {
            long[] nids = new long[remnantNotes.size()];
            for (int i = 0; i < remnantNotes.size(); i++) {
                nids[i] = remnantNotes.get(i);
            }
            getCol().remNotes(nids);
            getCol().save();
        }
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
