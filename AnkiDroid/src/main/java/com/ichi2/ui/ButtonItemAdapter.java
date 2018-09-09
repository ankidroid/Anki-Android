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

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
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

    private final ArrayList<String> items;
    private ItemCallback itemCallback;
    private ButtonCallback buttonCallback;

    public ButtonItemAdapter(ArrayList<String> items) {
        this.items = items;
    }

    public void remove(String searchName) {
        items.remove(searchName);
    }

    public void setCallbacks(ItemCallback itemCallback, ButtonCallback buttonCallback) {
        this.itemCallback = itemCallback;
        this.buttonCallback = buttonCallback;
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
        holder.title.setText(items.get(position));
        holder.button.setTag(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface ItemCallback {
        void onItemClicked(String searchName);
    }

    public interface ButtonCallback {
        void onButtonClicked(String searchName);
    }

    class ButtonVH extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final TextView title;
        private final ImageButton button;
        private final ButtonItemAdapter adapter;

        ButtonVH(View itemView, ButtonItemAdapter adapter) {
            super(itemView);
            title = itemView.findViewById(R.id.card_browser_my_search_name_textview);
            button = itemView.findViewById(R.id.card_browser_my_search_remove_button);

            this.adapter = adapter;
            itemView.setOnClickListener(this);
            button.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (adapter.itemCallback == null) {
                return;
            }
            if (view instanceof ImageButton) {
                adapter.buttonCallback.onButtonClicked(items.get(getAdapterPosition()));
            }
            else {
                adapter.itemCallback.onItemClicked(items.get(getAdapterPosition()));
            }
        }
    }


    /**
     * Ensure our strings are sorted alphabetically - call this explicitly after changing
     * the saved searches in any way, prior to displaying them again
     */
    public void notifyAdapterDataSetChanged() {
        Collections.sort(items, String::compareToIgnoreCase);
        super.notifyDataSetChanged();
    }
}
