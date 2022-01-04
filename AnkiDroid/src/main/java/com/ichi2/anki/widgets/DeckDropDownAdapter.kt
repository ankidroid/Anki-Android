/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ichi2.anki.R;

import com.ichi2.libanki.Deck;

import java.util.List;


public final class DeckDropDownAdapter extends BaseAdapter {

    public interface SubtitleListener {
        String getSubtitleText();
    }

    private final Context mContext;
    private final List<Deck> mDecks;

    public DeckDropDownAdapter(Context context, List<Deck> decks) {
        this.mContext = context;
        this.mDecks = decks;
    }

    static class DeckDropDownViewHolder {
        public TextView deckNameView;
        public TextView deckCountsView;
    }


    @Override
    public int getCount() {
        return mDecks.size() + 1;
    }


    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return null;
        } else {
            return mDecks.get(position + 1);
        }
    }


    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DeckDropDownViewHolder viewHolder;
        TextView deckNameView;
        TextView deckCountsView;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.dropdown_deck_selected_item, parent, false);
            deckNameView = convertView.findViewById(R.id.dropdown_deck_name);
            deckCountsView = convertView.findViewById(R.id.dropdown_deck_counts);
            viewHolder = new DeckDropDownViewHolder();
            viewHolder.deckNameView = deckNameView;
            viewHolder.deckCountsView = deckCountsView;
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (DeckDropDownViewHolder) convertView.getTag();
            deckNameView = viewHolder.deckNameView;
            deckCountsView = viewHolder.deckCountsView;
        }
        if (position == 0) {
            deckNameView.setText(mContext.getResources().getString(R.string.card_browser_all_decks));
        } else {
            Deck deck = mDecks.get(position - 1);
            String deckName = deck.getString("name");
            deckNameView.setText(deckName);
        }
        deckCountsView.setText(((SubtitleListener) mContext).getSubtitleText());
        return convertView;
    }


    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView deckNameView;
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.dropdown_deck_item, parent, false);
            deckNameView = convertView.findViewById(R.id.dropdown_deck_name);
            convertView.setTag(deckNameView);
        } else {
            deckNameView = (TextView) convertView.getTag();
        }
        if (position == 0) {
            deckNameView.setText(mContext.getResources().getString(R.string.card_browser_all_decks));
        } else {
            Deck deck = mDecks.get(position - 1);
            String deckName = deck.getString("name");
            deckNameView.setText(deckName);
        }
        return convertView;
    }
}
