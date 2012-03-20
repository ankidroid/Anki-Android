/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki2.R;
import com.ichi2.libanki.Collection;

public class CramDeckFragment extends Fragment {

	private EditText mCramDeckName;
	private LinearLayout mDecks;
	private TextView mDeckLabel;
	private EditText mSteps;
//	private EditText mOrder;
	private EditText mLimit;
	private EditText mInterval;
	private Button mCreateButton;
	private Button mCancelButton;

	private Collection mCol;
	private JSONObject mDeck;

    public static CramDeckFragment newInstance(int index) {
    	CramDeckFragment f = new CramDeckFragment();
        Bundle args = new Bundle();
        args.putInt("index", index);
        f.setArguments(args);

        return f;
    }

    public int getShownIndex() {
        return getArguments().getInt("index", 0);
    }

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (container == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }

        View main = inflater.inflate(R.layout.cram_deck, null);
        mCramDeckName = (EditText) main.findViewById(R.id.cram_deck_name);
        mCramDeckName.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				if (mCramDeckName.getText().length() == 0) {
					mCreateButton.setEnabled(false);
				} else {
					mCreateButton.setEnabled(true);
				}
			}
			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1,
					int arg2, int arg3) {
			}
			@Override
			public void onTextChanged(CharSequence arg0, int arg1,
					int arg2, int arg3) {
			}					
		});
        mDecks = (LinearLayout) main.findViewById(R.id.cram_deck_decks_button);
        mDeckLabel = (TextView) main.findViewById(R.id.cram_deck_decks_text);
        mDecks.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO
			}
        });
        mSteps = (EditText) main.findViewById(R.id.cram_deck_steps);
//        mOrder
        mLimit = (EditText) main.findViewById(R.id.cram_deck_limit);
        mInterval = (EditText) main.findViewById(R.id.cram_deck_interval);
        mCreateButton = (Button) main.findViewById(R.id.cram_deck_create);
        mCreateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					mDeck.put("steps", DeckOptions.getDelays(mSteps.getText().toString()));
//					mDeck.put("search", )
//					mDeck.put("order", value);
					mDeck.put("limit", Integer.parseInt(mLimit.getText().toString()));
					mDeck.put("fmult", Integer.parseInt(mLimit.getText().toString()) / 100.0);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				ft.remove(CramDeckFragment.this);
				ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
				ft.commit();
			}
        });
        mCancelButton = (Button) main.findViewById(R.id.cram_deck_cancel);
        mCancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				FragmentTransaction ft = getFragmentManager().beginTransaction();
				ft.remove(CramDeckFragment.this);
				ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
				ft.commit();
			}
        });

        mCol = Collection.currentCollection();
        if (mCol == null) {
        	return null;
        }
        mDeck = mCol.getDecks().current();
		try {
//	       TODO: mDeckLabel.setText(mDeck.getString("search"));
	        mSteps.setText(DeckOptions.getDelays(mDeck.getJSONArray("delays")));
//	        mOrder
	        mLimit.setText(mDeck.getInt("limit"));
	        mInterval.setText((int)(mDeck.getDouble("fmult") * 100));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
        return main;
    }


}
