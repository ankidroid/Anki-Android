package com.ichi2.anki.widgets;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ichi2.anki.R;

import com.ichi2.utils.JSONObject;

import java.util.ArrayList;



public final class DeckDropDownAdapter extends BaseAdapter {

    public interface SubtitleListener {
        public String getSubtitleText();
    }

    private Context context;
    private ArrayList<JSONObject> decks;

    public DeckDropDownAdapter(Context context, ArrayList<JSONObject> decks) {
        this.context = context;
        this.decks = decks;
    }

    static class DeckDropDownViewHolder {
        public TextView deckNameView;
        public TextView deckCountsView;
    }


    @Override
    public int getCount() {
        return decks.size() + 1;
    }


    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return null;
        } else {
            return decks.get(position + 1);
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
            convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_selected_item, parent, false);
            deckNameView = (TextView) convertView.findViewById(R.id.dropdown_deck_name);
            deckCountsView = (TextView) convertView.findViewById(R.id.dropdown_deck_counts);
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
            deckNameView.setText(context.getResources().getString(R.string.deck_summary_all_decks));
        } else {
            JSONObject deck = decks.get(position - 1);
            String deckName = deck.getString("name");
            deckNameView.setText(deckName);
        }
        deckCountsView.setText(((SubtitleListener) context).getSubtitleText());
        return convertView;
    }


    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        TextView deckNameView;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.dropdown_deck_item, parent, false);
            deckNameView = (TextView) convertView.findViewById(R.id.dropdown_deck_name);
            convertView.setTag(deckNameView);
        } else {
            deckNameView = (TextView) convertView.getTag();
        }
        if (position == 0) {
            deckNameView.setText(context.getResources().getString(R.string.deck_summary_all_decks));
        } else {
            JSONObject deck = decks.get(position - 1);
            String deckName = deck.getString("name");
            deckNameView.setText(deckName);
        }
        return convertView;
    }
}
