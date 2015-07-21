/****************************************************************************************
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Sched;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.ViewHolder> {

    private LayoutInflater mLayoutInflater;
    private List<Sched.DeckDueTreeNode> mDeckList;
    private int mZeroCountColor;
    private int mNewCountColor;
    private int mLearnCountColor;
    private int mReviewCountColor;
    private int mRowDefaultColor;
    private int mRowCurrentColor;
    private int mDeckNameDefaultColor;
    private int mDeckNameDynColor;

    // Listeners
    private View.OnClickListener mDeckClickListener;
    private View.OnClickListener mDeckExpanderClickListener;
    private View.OnLongClickListener mDeckLongClickListener;

    private Collection mCol;

    // Totals accumulated as each deck is processed
    private int mNew;
    private int mLrn;
    private int mRev;

    // ViewHolder class to save inflated views for recycling
    public class ViewHolder extends RecyclerView.ViewHolder {
        public RelativeLayout deckLayout;
        public TextView deckExpander;
        public TextView deckName;
        public TextView deckNew, deckLearn, deckRev;

        public ViewHolder(View v) {
            super(v);
            deckLayout = (RelativeLayout) v.findViewById(R.id.DeckPickerHoriz);
            deckExpander = (TextView) v.findViewById(R.id.DeckPickerExpander);
            deckName = (TextView) v.findViewById(R.id.DeckPickerName);
            deckNew = (TextView) v.findViewById(R.id.deckpicker_new);
            deckLearn = (TextView) v.findViewById(R.id.deckpicker_lrn);
            deckRev = (TextView) v.findViewById(R.id.deckpicker_rev);
        }
    }

    public DeckAdapter(LayoutInflater layoutInflater, Context context) {
        mLayoutInflater = layoutInflater;
        mCol = CollectionHelper.getInstance().getCol(context);
        mDeckList = new ArrayList<>();
        // Get the colors from the theme attributes
        int[] attrs = new int[] {
                R.attr.zeroCountColor,
                R.attr.newCountColor,
                R.attr.learnCountColor,
                R.attr.reviewCountColor,
                android.R.attr.colorBackground,
                R.attr.currentDeckBackgroundColor,
                android.R.attr.textColor,
                R.attr.dynDeckColor};
        TypedArray ta = context.obtainStyledAttributes(attrs);
        Resources res = context.getResources();
        mZeroCountColor = ta.getColor(0, res.getColor(R.color.zero_count));
        mNewCountColor = ta.getColor(1, res.getColor(R.color.new_count));
        mLearnCountColor = ta.getColor(2, res.getColor(R.color.learn_count));
        mReviewCountColor = ta.getColor(3, res.getColor(R.color.review_count));
        mRowDefaultColor = ta.getColor(4, res.getColor(R.color.black));
        mRowCurrentColor = ta.getColor(5, res.getColor(R.color.deckadapter_row_current));
        mDeckNameDefaultColor = ta.getColor(6, res.getColor(R.color.black));
        mDeckNameDynColor = ta.getColor(7, res.getColor(R.color.deckadapter_deck_name_dyn));
        ta.recycle();
    }

    public void setDeckClickListener(View.OnClickListener listener) {
        mDeckClickListener = listener;
    }

    public void setDeckExpanderClickListener(View.OnClickListener listener) {
        mDeckExpanderClickListener = listener;
    }

    public void setDeckLongClickListener(View.OnLongClickListener listener) {
        mDeckLongClickListener = listener;
    }


    /**
     * Consume a list of {@link Sched.DeckDueTreeNode}s to render a new deck list.
     */
    public void buildDeckList(List<Sched.DeckDueTreeNode> nodes) {
        mDeckList.clear();
        mNew = mLrn = mRev = 0;
        processNodes(nodes);
        notifyDataSetChanged();
    }


    @Override
    public DeckAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = mLayoutInflater.inflate(R.layout.deck_item, parent, false);
        return new ViewHolder(v);
    }


    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // Update views for this node
        Sched.DeckDueTreeNode node = mDeckList.get(position);
        boolean collapsed = mCol.getDecks().get(node.did).optBoolean("collapsed", false);

        // Set expander and make it clickable if needed
        holder.deckExpander.setText(deckExpander(node.depth, collapsed, node.children.size()));
        if (node.children.size() > 0) {
            holder.deckExpander.setTag(node.did);
            holder.deckExpander.setOnClickListener(mDeckExpanderClickListener);
        }

        // Set background colour. The current deck has its own color
        if (node.did == mCol.getDecks().current().optLong("id")) {
            holder.deckLayout.setBackgroundColor(mRowCurrentColor);
        } else {
            holder.deckLayout.setBackgroundColor(mRowDefaultColor);
        }

        // Set deck name and colour. Filtered decks have their own colour
        holder.deckName.setText(node.names[0]);
        if (mCol.getDecks().isDyn(node.did)) {
            holder.deckName.setTextColor(mDeckNameDynColor);
        } else {
            holder.deckName.setTextColor(mDeckNameDefaultColor);
        }

        // Set the card counts and their colors
        holder.deckNew.setText(String.valueOf(node.newCount));
        holder.deckNew.setTextColor((node.newCount == 0) ? mZeroCountColor : mNewCountColor);
        holder.deckLearn.setText(String.valueOf(node.lrnCount));
        holder.deckLearn.setTextColor((node.lrnCount == 0) ? mZeroCountColor : mLearnCountColor);
        holder.deckRev.setText(String.valueOf(node.revCount));
        holder.deckRev.setTextColor((node.revCount == 0) ? mZeroCountColor : mReviewCountColor);

        // Store deck ID in layout's tag for easy retrieval in our click listeners
        holder.deckLayout.setTag(node.did);

        // Set click listeners
        holder.deckLayout.setOnClickListener(mDeckClickListener);
        holder.deckLayout.setOnLongClickListener(mDeckLongClickListener);
    }

    @Override
    public int getItemCount() {
        return mDeckList.size();
    }


    /**
     * Returns the name of the deck to be displayed in the deck list.
     * <p/>
     * Various properties of a deck are indicated to the user by the deck name in the deck list
     * (as opposed to additional native UI elements). This includes the amount of indenting
     * for nested decks based on depth and an indicator of collapsed state.
     */
    private String deckExpander(int depth, boolean collapsed, int children) {
        String s;
        if (collapsed) {
            // add arrow pointing right if collapsed
            s = "\u25B7 ";
        } else if (children > 0) {
            // add arrow pointing down if deck has children
            s = "\u25BD ";
        } else {
            // add empty spaces
            s = "\u2009\u2009\u2009 ";
        }
        if (depth == 0) {
            return s;
        } else {
            // Add 4 spaces for every level of nesting
            return new String(new char[depth]).replace("\0", "\u2009\u2009\u2009 ") + s;
        }
    }


    private void processNodes(List<Sched.DeckDueTreeNode> nodes) {
        processNodes(nodes, 0);
    }


    private void processNodes(List<Sched.DeckDueTreeNode> nodes, int depth) {
        for (Sched.DeckDueTreeNode node : nodes) {
            // If the default deck is empty, hide it by not adding it to the deck list.
            // We don't hide it if it's the only deck or if it has sub-decks.
            if (node.did == 1 && nodes.size() > 1 && node.children.size() == 0) {
                if (mCol.getDb().queryScalar("select 1 from cards where did = 1") == 0) {
                    continue;
                }
            }
            // If any of this node's parents are collapsed, don't add it to the deck list
            for (JSONObject parent : mCol.getDecks().parents(node.did)) {
                if (parent.optBoolean("collapsed")) {
                    return;
                }
            }
            mDeckList.add(node);
            // Keep track of the depth. It's used to determine visual properties like indenting later
            node.depth = depth;

            // Add this node's counts to the totals if it's a parent deck
            if (depth == 0) {
                mNew += node.newCount;
                mLrn += node.lrnCount;
                mRev += node.revCount;
            }
            // Process sub-decks
            processNodes(node.children, depth + 1);
        }
    }


    /**
     * Return the position of the deck in the deck list. If the deck is a child of a collapsed deck
     * (i.e., not visible in the deck list), then the position of the parent deck is returned instead.
     *
     * An invalid deck ID will return position 0.
     */
    public int findDeckPosition(long did) {
        for (int i = 0; i < mDeckList.size(); i++) {
            if (mDeckList.get(i).did == did) {
                return i;
            }
        }
        // If the deck is not in our list, we search again using the immediate parent
        ArrayList<JSONObject> parents = mCol.getDecks().parents(did);
        if (parents.size() == 0) {
            return 0;
        } else {
            return findDeckPosition(parents.get(parents.size() - 1).optLong("id", 0));
        }
    }


    public int getEta() {
        return mCol.getSched().eta(new int[]{mNew, mLrn, mRev});
    }

    public int getDue() {
        return mNew + mLrn + mRev;
    }
}