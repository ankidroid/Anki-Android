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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.anki.servicelayer.DeckService;
import com.ichi2.libanki.Collection;

import com.ichi2.libanki.Deck;
import com.ichi2.libanki.sched.AbstractDeckTreeNode;
import com.ichi2.utils.FilterResultsUtils;
import com.ichi2.libanki.sched.Counts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO;
import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES;

public class DeckAdapter<T extends AbstractDeckTreeNode<T>> extends RecyclerView.Adapter<DeckAdapter.ViewHolder> implements Filterable {

    /* Make the selected deck roughly half transparent if there is a background */
    public static final double SELECTED_DECK_ALPHA_AGAINST_BACKGROUND = 0.45;

    private final LayoutInflater mLayoutInflater;
    private final List<T> mDeckList;
    /** A subset of mDeckList (currently displayed) */
    private final List<AbstractDeckTreeNode<?>> mCurrentDeckList = new ArrayList<>();
    private final int mZeroCountColor;
    private final int mNewCountColor;
    private final int mLearnCountColor;
    private final int mReviewCountColor;
    private final int mRowCurrentDrawable;
    private final int mDeckNameDefaultColor;
    private final int mDeckNameDynColor;
    private final Drawable mExpandImage;
    private final Drawable mCollapseImage;
    private final Drawable mNoExpander = new ColorDrawable(Color.TRANSPARENT);

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
    private boolean mNumbersComputed;

    // Flags
    private boolean mHasSubdecks;

    // Whether we have a background (so some items should be partially transparent).
    private boolean mPartiallyTransparentForBackground;

    // ViewHolder class to save inflated views for recycling
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final RelativeLayout deckLayout;
        public final LinearLayout countsLayout;
        public final ImageButton deckExpander;
        public final ImageButton indentView;
        public final TextView deckName;
        public final TextView deckNew;
        public final TextView deckLearn;
        public final TextView deckRev;

        public ViewHolder(View v) {
            super(v);
            deckLayout = v.findViewById(R.id.DeckPickerHoriz);
            countsLayout = v.findViewById(R.id.counts_layout);
            deckExpander = v.findViewById(R.id.deckpicker_expander);
            indentView = v.findViewById(R.id.deckpicker_indent);
            deckName = v.findViewById(R.id.deckpicker_name);
            deckNew = v.findViewById(R.id.deckpicker_new);
            deckLearn = v.findViewById(R.id.deckpicker_lrn);
            deckRev = v.findViewById(R.id.deckpicker_rev);
        }
    }

    public DeckAdapter(LayoutInflater layoutInflater, Context context) {
        mLayoutInflater = layoutInflater;
        mDeckList = new ArrayList<>((mCol == null) ? 10 : mCol.getDecks().count());
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
        mExpandImage.setAutoMirrored(true);
        mCollapseImage = ta.getDrawable(8);
        mCollapseImage.setAutoMirrored(true);
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

    /** Sets whether the control should have partial transparency to allow a background to be seen */
    public void enablePartialTransparencyForBackground(boolean isTransparent) {
        mPartiallyTransparentForBackground = isTransparent;
    }


    /**
     * Consume a list of {@link AbstractDeckTreeNode}s to render a new deck list.
     * @param filter The string to filter the deck by
     */
    public void buildDeckList(List<T> nodes, Collection col, @Nullable CharSequence filter) {
        mCol = col;
        mDeckList.clear();
        mCurrentDeckList.clear();
        mNew = mLrn = mRev = 0;
        mNumbersComputed = true;
        mHasSubdecks = false;
        processNodes(nodes);
        // Filtering performs notifyDataSetChanged after the async work is complete
        getFilter().filter(filter);
    }

    public AbstractDeckTreeNode<?> getNodeByDid(long did) {
        int pos = findDeckPosition(did);
        return getDeckList().get(pos);
    }


    @NonNull
    @Override
    public DeckAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = mLayoutInflater.inflate(R.layout.deck_item, parent, false);
        return new ViewHolder(v);
    }


    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // Update views for this node
        AbstractDeckTreeNode<?> node = mCurrentDeckList.get(position);
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

        if (node.hasChildren()) {
            holder.deckExpander.setTag(node.getDid());
            holder.deckExpander.setOnClickListener(mDeckExpanderClickListener);
        } else {
            holder.deckExpander.setClickable(false);
        }
        holder.deckLayout.setBackgroundResource(mRowCurrentDrawable);
        // Set background colour. The current deck has its own color
        if (isCurrentlySelectedDeck(node)) {
            holder.deckLayout.setBackgroundResource(mRowCurrentDrawable);
            if (mPartiallyTransparentForBackground) {
                setBackgroundAlpha(holder.deckLayout, SELECTED_DECK_ALPHA_AGAINST_BACKGROUND);
            }
        } else {
            // Ripple effect
            int[] attrs = new int[] {android.R.attr.selectableItemBackground};
            TypedArray ta = holder.deckLayout.getContext().obtainStyledAttributes(attrs);
            holder.deckLayout.setBackgroundResource(ta.getResourceId(0, 0));
            ta.recycle();
        }
        // Set deck name and colour. Filtered decks have their own colour
        holder.deckName.setText(node.getLastDeckNameComponent());
        if (mCol.getDecks().isDyn(node.getDid())) {
            holder.deckName.setTextColor(mDeckNameDynColor);
        } else {
            holder.deckName.setTextColor(mDeckNameDefaultColor);
        }

        // Set the card counts and their colors
        if (node.shouldDisplayCounts()) {
            holder.deckNew.setText(String.valueOf(node.getNewCount()));
            holder.deckNew.setTextColor((node.getNewCount() == 0) ? mZeroCountColor : mNewCountColor);
            holder.deckLearn.setText(String.valueOf(node.getLrnCount()));
            holder.deckLearn.setTextColor((node.getLrnCount() == 0) ? mZeroCountColor : mLearnCountColor);
            holder.deckRev.setText(String.valueOf(node.getRevCount()));
            holder.deckRev.setTextColor((node.getRevCount() == 0) ? mZeroCountColor : mReviewCountColor);
        }

        // Store deck ID in layout's tag for easy retrieval in our click listeners
        holder.deckLayout.setTag(node.getDid());
        holder.countsLayout.setTag(node.getDid());

        // Set click listeners
        holder.deckLayout.setOnClickListener(mDeckClickListener);
        holder.deckLayout.setOnLongClickListener(mDeckLongClickListener);
        holder.countsLayout.setOnClickListener(mCountsClickListener);
    }


    private void setBackgroundAlpha(View view, @SuppressWarnings("SameParameterValue") double alphaPercentage) {
        Drawable background = view.getBackground().mutate();
        background.setAlpha((int) (255 * alphaPercentage));
        view.setBackground(background);
    }


    private boolean isCurrentlySelectedDeck(AbstractDeckTreeNode<?> node) {
        return node.getDid() == mCol.getDecks().current().optLong("id");
    }


    @Override
    public int getItemCount() {
        return mCurrentDeckList.size();
    }


    private void setDeckExpander(ImageButton expander, ImageButton indent, AbstractDeckTreeNode<?> node){
        boolean collapsed = mCol.getDecks().get(node.getDid()).optBoolean("collapsed", false);
        // Apply the correct expand/collapse drawable
        if (node.hasChildren()) {
            expander.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            if (collapsed) {
                expander.setImageDrawable(mExpandImage);
                expander.setContentDescription(expander.getContext().getString(R.string.expand));
            } else  {
                expander.setImageDrawable(mCollapseImage);
                expander.setContentDescription(expander.getContext().getString(R.string.collapse));
            }
        } else {
            expander.setImageDrawable(mNoExpander);
            expander.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        // Add some indenting for each nested level
        int width = (int) indent.getResources().getDimension(R.dimen.keyline_1) * node.getDepth();
        indent.setMinimumWidth(width);
    }


    private void processNodes(List<T> nodes) {
        for (T node : nodes) {
            // If the default deck is empty, hide it by not adding it to the deck list.
            // We don't hide it if it's the only deck or if it has sub-decks.
            if (node.getDid() == 1 && nodes.size() > 1 && !node.hasChildren()) {
                if (!DeckService.defaultDeckHasCards(mCol)) {
                    continue;
                }
            }
            // If any of this node's parents are collapsed, don't add it to the deck list
            for (Deck parent : mCol.getDecks().parents(node.getDid())) {
                mHasSubdecks = true;    // If a deck has a parent it means it's a subdeck so set a flag
                if (parent.optBoolean("collapsed")) {
                    return;
                }
            }
            mDeckList.add(node);
            mCurrentDeckList.add(node);

            // Add this node's counts to the totals if it's a parent deck
            if (node.getDepth() == 0) {
                if (node.shouldDisplayCounts()) {
                    mNew += node.getNewCount();
                    mLrn += node.getLrnCount();
                    mRev += node.getRevCount();
                }
            }
            // Process sub-decks
            processNodes(node.getChildren());
        }
    }


    /**
     * Return the position of the deck in the deck list. If the deck is a child of a collapsed deck
     * (i.e., not visible in the deck list), then the position of the parent deck is returned instead.
     *
     * An invalid deck ID will return position 0.
     */
    public int findDeckPosition(long did) {
        for (int i = 0; i < mCurrentDeckList.size(); i++) {
            if (mCurrentDeckList.get(i).getDid() == did) {
                return i;
            }
        }
        // If the deck is not in our list, we search again using the immediate parent
        List<Deck> parents = mCol.getDecks().parents(did);
        if (parents.isEmpty()) {
            return 0;
        } else {
            return findDeckPosition(parents.get(parents.size() - 1).optLong("id", 0));
        }
    }

    @Nullable
    public Integer getEta() {
        if (mNumbersComputed) {
            return mCol.getSched().eta(new Counts(mNew, mLrn, mRev));
        } else {
            return null;
        }
    }

    @Nullable
    public Integer getDue() {
        if (mNumbersComputed) {
            return mNew + mLrn + mRev;
        } else {
            return null;
        }
    }

    private List<AbstractDeckTreeNode<?>> getDeckList() {
        return mCurrentDeckList;
    }


    @Override
    public Filter getFilter() {
        return new DeckFilter();
    }


    private class DeckFilter extends Filter {
        private final @NonNull ArrayList<AbstractDeckTreeNode<?>> mFilteredDecks = new ArrayList<>();
        private DeckFilter() {
            super();
        }

        private List<T> getAllDecks() {
            return mDeckList;
        }

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            mFilteredDecks.clear();
            mFilteredDecks.ensureCapacity(mCol.getDecks().count());

            List<T> allDecks = getAllDecks();
            if (TextUtils.isEmpty(constraint)) {
                mFilteredDecks.addAll(allDecks);
            } else {
                final String filterPattern = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                List<T> filteredDecks = filterDecks(filterPattern, allDecks);
                mFilteredDecks.addAll(filteredDecks);
            }

            return FilterResultsUtils.fromCollection(mFilteredDecks);
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mCurrentDeckList.clear();
            mCurrentDeckList.addAll(mFilteredDecks);
            notifyDataSetChanged();
        }


        private List<T> filterDecks(String filterPattern, List<T> allDecks) {
            ArrayList<T> ret = new ArrayList<>(allDecks.size());
            for (T tag : allDecks) {
                T node = filterDeckInternal(filterPattern, tag);
                if (node != null) {
                    ret.add(node);
                }
            }
            return ret;
        }

        @Nullable
        private T filterDeckInternal(String filterPattern, T root) {

            // If a deck contains the string, then all its children are valid
            if (containsFilterString(filterPattern, root)) {
                return root;
            }

            List<T> children = root.getChildren();
            List<T> ret = new ArrayList<>(children.size());
            for (T child : children) {
                T returned = filterDeckInternal(filterPattern, child);
                if (returned != null) {
                    ret.add(returned);
                }
            }

            // If any of a deck's children contains the search string, then the deck is valid
            return ret.isEmpty() ? null : root.withChildren(ret);
        }


        private boolean containsFilterString(String filterPattern, T root) {
            String deckName = root.getFullDeckName();
            return deckName.toLowerCase(Locale.getDefault()).contains(filterPattern) || deckName.toLowerCase(Locale.ROOT).contains(filterPattern);
        }
    }
}
