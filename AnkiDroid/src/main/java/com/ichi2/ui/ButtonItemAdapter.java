//noinspection MissingCopyrightHeader #8659
/*
 * The MIT License (MIT)

 Copyright (c) 2014-2016 Aidan Michael Follestad

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */

package com.ichi2.ui;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.Collections;

/**
 * RecyclerView.Adapter class copied almost completely from the Material Dialogs library example
 * {@see <a href="https://github.com/afollestad/material-dialogs/blob/0.9.6.0/sample/src/main/java/com/afollestad/materialdialogssample/ButtonItemAdapter.java>ButtonItemAdapter.java</a>
 */
public class ButtonItemAdapter extends RecyclerView.Adapter<ButtonItemAdapter.ButtonVH> {

    private final ArrayList<String> mItems;
    private ItemCallback mItemCallback;
    private ButtonCallback mButtonCallback;

    public ButtonItemAdapter(ArrayList<String> items) {
        this.mItems = items;
    }

    public void remove(String searchName) {
        mItems.remove(searchName);
    }

    public void setCallbacks(ItemCallback itemCallback, ButtonCallback buttonCallback) {
        this.mItemCallback = itemCallback;
        this.mButtonCallback = buttonCallback;
    }

    @Override
    public @NonNull ButtonVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view =
                LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.card_browser_item_my_searches_dialog, parent, false);
        return new ButtonVH(view, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ButtonVH holder, int position) {
        holder.mTitle.setText(mItems.get(position));
        holder.mButton.setTag(mItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public interface ItemCallback {
        void onItemClicked(String searchName);
    }

    public interface ButtonCallback {
        void onButtonClicked(String searchName);
    }

    class ButtonVH extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final TextView mTitle;
        private final ImageButton mButton;
        private final ButtonItemAdapter mAdapter;

        private ButtonVH(View itemView, ButtonItemAdapter adapter) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.card_browser_my_search_name_textview);
            mButton = itemView.findViewById(R.id.card_browser_my_search_remove_button);

            this.mAdapter = adapter;
            itemView.setOnClickListener(this);
            mButton.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mAdapter.mItemCallback == null) {
                return;
            }
            if (view instanceof ImageButton) {
                mAdapter.mButtonCallback.onButtonClicked(mItems.get(getBindingAdapterPosition()));
            }
            else {
                mAdapter.mItemCallback.onItemClicked(mItems.get(getBindingAdapterPosition()));
            }
        }
    }


    /**
     * Ensure our strings are sorted alphabetically - call this explicitly after changing
     * the saved searches in any way, prior to displaying them again
     */
    public void notifyAdapterDataSetChanged() {
        Collections.sort(mItems, String::compareToIgnoreCase);
        super.notifyDataSetChanged();
    }
}
