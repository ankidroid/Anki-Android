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
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;
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
    private int mRowCurrentDrawable;
    private int mDeckNameDefaultColor;
    private int mDeckNameDynColor;
    private Drawable mExpandImage;
    private Drawable mCollapseImage;
    private Drawable mNoExpander = new ColorDrawable(Color.TRANSPARENT);

    // Listeners
    private View.OnClickListener mDeckClickListener;
    private View.OnClickListener mDeckExpanderClickListener;
    private View.OnLongClickListener mDeckLongClickListener;
    private View.OnClickListener mCountsClickListener;

    private Collection mCol;

    // Totals accumulated as each deck is processed
    private int mNew;
    private int mLrn;
    private int mRev;

    // Flags
    private boolean mHasSubdecks;

    // ViewHolder class to save inflated views for recycling
    public class ViewHolder extends RecyclerView.ViewHolder {
        public RelativeLayout deckLayout;
        public LinearLayout countsLayout;
        public ImageButton deckExpander;
        public ImageButton indentView;
        public TextView deckName;
        public TextView deckNew, deckLearn, deckRev;

        public ViewHolder(View v) {
            super(v);
            deckLayout = (RelativeLayout) v.findViewById(R.id.DeckPickerHoriz);
            countsLayout = (LinearLayout) v.findViewById(R.id.counts_layout);
            deckExpander = (ImageButton) v.findViewById(R.id.deckpicker_expander);
            indentView = (ImageButton) v.findViewById(R.id.deckpicker_indent);
            deckName = (TextView) v.findViewById(R.id.deckpicker_name);
            deckNew = (TextView) v.findViewById(R.id.deckpicker_new);
            deckLearn = (TextView) v.findViewById(R.id.deckpicker_lrn);
            deckRev = (TextView) v.findViewById(R.id.deckpicker_rev);
        }
    }

    public DeckAdapter(LayoutInflater layoutInflater, Context context) {
        mLayoutInflater = layoutInflater;
        mDeckList = new ArrayList<>();
        // Get the colors from the theme attributes
        int[] attrs = new int[] {
                R.attr.zeroCountColor,
                R.attr.newCountColor,
                R.attr.learnCountColor,
                R.attr.reviewCountColor,
                R.attr.currentDeckBackground,
                android.R.attr.textColor,
                R.attr.dynDeckColor,
                R.attr.expandRef,
                R.attr.collapseRef };
        TypedArray ta = context.obtainStyledAttributes(attrs);
        mZeroCountColor = ta.getColor(0, ContextCompat.getColor(context, R.color.black));
        mNewCountColor = ta.getColor(1, ContextCompat.getColor(context, R.color.black));
        mLearnCountColor = ta.getColor(2, ContextCompat.getColor(context, R.color.black));
        mReviewCountColor = ta.getColor(3, ContextCompat.getColor(context, R.color.black));
        mRowCurrentDrawable = ta.getResourceId(4, 0);
        mDeckNameDefaultColor = ta.getColor(5, ContextCompat.getColor(context, R.color.black));
        mDeckNameDynColor = ta.getColor(6, ContextCompat.getColor(context, R.color.material_blue_A700));
        mExpandImage = ta.getDrawable(7);
        mCollapseImage = ta.getDrawable(8);
        ta.recycle();
    }

    public void setDeckClickListener(View.OnClickListener listener) {
        mDeckClickListener = listener;
    }

    public void setCountsClickListener(View.OnClickListener listener) {
        mCountsClickListener = listener;
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
    public void buildDeckList(List<Sched.DeckDueTreeNode> nodes, Collection col) {
        mCol = col;
        mDeckList.clear();
        mNew = mLrn = mRev = 0;
        mHasSubdecks = false;
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
        // Set the expander icon and padding according to whether or not there are any subdecks
        RelativeLayout deckLayout = holder.deckLayout;
        int rightPadding = (int) deckLayout.getResources().getDimension(R.dimen.deck_picker_right_padding);
        if (mHasSubdecks) {
            int smallPadding = (int) deckLayout.getResources().getDimension(R.dimen.deck_picker_left_padding_small);
            deckLayout.setPadding(smallPadding, 0, rightPadding, 0);
            holder.deckExpander.setVisibility(View.VISIBLE);
            // Create the correct expander for this deck
            setDeckExpander(holder.deckExpander, holder.indentView, node);
        } else {
            holder.deckExpander.setVisibility(View.GONE);
            int normalPadding = (int) deckLayout.getResources().getDimension(R.dimen.deck_picker_left_padding);
            deckLayout.setPadding(normalPadding, 0, rightPadding, 0);
        }

        if (node.children.size() > 0) {
            holder.deckExpander.setTag(node.did);
            holder.deckExpander.setOnClickListener(mDeckExpanderClickListener);
        } else {
            holder.deckExpander.setOnClickListener(null);
        }
        holder.deckLayout.setBackgroundResource(mRowCurrentDrawable);
        // Set background colour. The current deck has its own color
        if (node.did == mCol.getDecks().current().optLong("id")) {
            holder.deckLayout.setBackgroundResource(mRowCurrentDrawable);
        } else {
            CompatHelper.getCompat().setSelectableBackground(holder.deckLayout);
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
        holder.countsLayout.setTag(node.did);

        // Set click listeners
        holder.deckLayout.setOnClickListener(mDeckClickListener);
        holder.deckLayout.setOnLongClickListener(mDeckLongClickListener);
        holder.countsLayout.setOnClickListener(mCountsClickListener);
    }

    @Override
    public int getItemCount() {
        return mDeckList.size();
    }


    private void setDeckExpander(ImageButton expander, ImageButton indent, Sched.DeckDueTreeNode node){
        boolean collapsed = mCol.getDecks().get(node.did).optBoolean("collapsed", false);
        // Apply the correct expand/collapse drawable
        if (collapsed) {
            expander.setImageDrawable(mExpandImage);
            expander.setContentDescription(expander.getContext().getString(R.string.expand));
        } else if (node.children.size() > 0) {
            expander.setImageDrawable(mCollapseImage);
            expander.setContentDescription(expander.getContext().getString(R.string.collapse));
        } else {
            expander.setImageDrawable(mNoExpander);
        }
        // Add some indenting for each nested level
        int width = (int) indent.getResources().getDimension(R.dimen.keyline_1) * node.depth;
        indent.setMinimumWidth(width);
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
                mHasSubdecks = true;    // If a deck has a parent it means it's a subdeck so set a flag
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
        List<JSONObject> parents = mCol.getDecks().parents(did);
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

    public List<Sched.DeckDueTreeNode> getDeckList() {
        return mDeckList;
    }
}