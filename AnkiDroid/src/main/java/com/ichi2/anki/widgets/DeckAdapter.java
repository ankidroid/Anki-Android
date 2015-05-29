package com.ichi2.anki.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.widget.*;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.libanki.*;

import java.util.List;

import timber.log.Timber;

public class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.ViewHolder> {

    private LayoutInflater mLayoutInflater;
    private List<Sched.DeckDueTreeNode> mDeckList;
    private int zeroCountColor;
    private int newCountColor;
    private int learnCountColor;
    private int reviewCountColor;
    private View.OnClickListener mDeckClickListener;
    private View.OnClickListener mDeckExpanderClickListener;
    private View.OnLongClickListener mDeckLongClickListener;
    private Context mContext;

    // ViewHolder class to save inflated views for recycling
    public class ViewHolder extends RecyclerView.ViewHolder {
        public RelativeLayout mDeckLayout;
        public TextView mDeckExpander;
        public TextView mDeckName;
        public TextView mDeckNew, mDeckLearn, mDeckRev;

        public ViewHolder(View v) {
            super(v);
            mDeckLayout = (RelativeLayout) v.findViewById(R.id.DeckPickerHoriz);
            mDeckExpander = (TextView) v.findViewById(R.id.DeckPickerExpander);
            mDeckName = (TextView) v.findViewById(R.id.DeckPickerName);
            mDeckNew = (TextView) v.findViewById(R.id.deckpicker_new);
            mDeckLearn = (TextView) v.findViewById(R.id.deckpicker_lrn);
            mDeckRev = (TextView) v.findViewById(R.id.deckpicker_rev);
        }
    }

    public DeckAdapter(List<Sched.DeckDueTreeNode> deckList, Resources resources,
                       LayoutInflater layoutInflater, Context context) {
        mLayoutInflater = layoutInflater;
        mDeckList = deckList;
        zeroCountColor = resources.getColor(R.color.zero_count);
        newCountColor = resources.getColor(R.color.new_count);
        learnCountColor = resources.getColor(R.color.learn_count);
        reviewCountColor = resources.getColor(R.color.review_count);
        mContext = context.getApplicationContext(); // don't hold ref to Activity
    }

    public void setDeckClickListener(View.OnClickListener mDeckClickListener) {
        this.mDeckClickListener = mDeckClickListener;
    }

    public void setDeckExpanderClickListener(View.OnClickListener mDeckExpanderClickListener) {
        this.mDeckExpanderClickListener = mDeckExpanderClickListener;
    }

    public void setDeckLongClickListener(View.OnLongClickListener mDeckLongClickListener) {
        this.mDeckLongClickListener = mDeckLongClickListener;
    }

    @Override
    public DeckAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = mLayoutInflater.inflate(R.layout.deck_item, parent, false);
        return new ViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // update views for this node
        Sched.DeckDueTreeNode node = mDeckList.get(position);
        Timber.d("DeckAdapter accessing collection...");
        Collection col = CollectionHelper.getInstance().getCol(mContext);
        boolean collapsed = col.getDecks().get(node.did).optBoolean("collapsed", false);

        // set expander and make expander clickable if needed
        holder.mDeckExpander.setText(deckExpander(node.depth, collapsed, node.children.size()));
        if (node.children.size() > 0) {
            holder.mDeckExpander.setClickable(false);
            holder.mDeckExpander.setTag(node.did);
            holder.mDeckExpander.setOnClickListener(mDeckExpanderClickListener);
        } else {
            holder.mDeckExpander.setClickable(false);
        }

        // set deck name
        holder.mDeckName.setText(node.names[0]);

        // set deck new card count and color
        holder.mDeckNew.setText(String.valueOf(node.newCount));
        holder.mDeckNew.setTextColor((node.newCount == 0) ?
                zeroCountColor : newCountColor);

        // set deck learn card count and color
        holder.mDeckLearn.setText(String.valueOf(node.lrnCount));
        holder.mDeckLearn.setTextColor((node.lrnCount == 0) ?
                zeroCountColor : learnCountColor);

        // set deck review card count and color
        holder.mDeckRev.setText(String.valueOf(node.revCount));
        holder.mDeckRev.setTextColor((node.revCount == 0) ?
                zeroCountColor : reviewCountColor);

        // store deck ID in layout's tag, for easy retrieval in our click listeners
        holder.mDeckLayout.setTag(node.did);

        // set click listeners
        holder.mDeckLayout.setOnClickListener(mDeckClickListener);
        holder.mDeckLayout.setOnLongClickListener(mDeckLongClickListener);

        // if this deck is the current deck, highlight it
        //  if (node.did == getCol().getDecks().current().optLong("id")) {
//                mDeckListView.setItemChecked(mOldDeckList.size(), true); // TODO
        //   }
    }

    @Override
    public int getItemCount() {
        // TODO
        // If the default deck is empty, hide it
        // We don't hide it if it's the only deck or if it has sub-decks
//            if (node.did == 1 && cnt > 1 && node.children.size() == 0) {
//                if (getCol().getDb().queryScalar("select 1 from cards where did = 1", false) == 0) {
//                    mHideDefaultDeck = true;
//                }
//            }

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
        String s = new String();
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
}