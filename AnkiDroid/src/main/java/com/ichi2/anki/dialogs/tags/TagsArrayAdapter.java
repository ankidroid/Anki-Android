/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.tags;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import com.ichi2.anki.R;
import com.ichi2.ui.CheckBoxTriStates;
import com.ichi2.utils.FilterResultsUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import static androidx.annotation.VisibleForTesting.*;

public class TagsArrayAdapter extends  RecyclerView.Adapter<TagsArrayAdapter.ViewHolder> implements Filterable {
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBoxTriStates mCheckBoxView;
        private final TextView mTextView;
        public ViewHolder(View itemView) {
            super(itemView);
            mCheckBoxView = itemView.findViewById(R.id.tags_dialog_tag_item_checkbox);
            mTextView = itemView.findViewById(R.id.tags_dialog_tag_item_text);
        }

        @VisibleForTesting(otherwise = NONE)
        public String getText() {
            return mTextView.getText().toString();
        }


        @VisibleForTesting(otherwise = NONE)
        public boolean isChecked() {
            return mCheckBoxView.isChecked();
        }


        @VisibleForTesting(otherwise = NONE)
        public CheckBoxTriStates.State getCheckboxState() {
            return mCheckBoxView.getState();
        }
    }



    /**
     * A reference to the {@link TagsList} passed.
     */
    @NonNull
    private final TagsList mTags;
    /**
     * A subset of all tags in {@link #mTags} satisfying the user's search.
     *
     * it will be null if the user search term is empty, in that case
     * the adapter should use {@link #mTags} instead to access full list.
     */
    @Nullable
    private List<String> mFilteredList;

    public TagsArrayAdapter(@NonNull TagsList tags) {
        mTags = tags;
        mFilteredList = null;
        sortData();
    }

    public void sortData() {
        mTags.sort();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tags_item_list_dialog, parent, false);

        ViewHolder vh = new ViewHolder(v.findViewById(R.id.tags_dialog_tag_item));
        vh.itemView.setOnClickListener(view -> {
            CheckBoxTriStates checkBox = vh.mCheckBoxView;
            String tag = vh.mTextView.getText().toString();
            checkBox.toggle();
            if (checkBox.getState() == CheckBoxTriStates.State.CHECKED) {
                mTags.check(tag);
            } else if (checkBox.getState() == CheckBoxTriStates.State.UNCHECKED) {
                mTags.uncheck(tag);
            }
        });
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String tag = getTagByIndex(position);
        holder.mTextView.setText(tag);
        if (mTags.isIndeterminate(tag)) {
            holder.mCheckBoxView.setState(CheckBoxTriStates.State.INDETERMINATE);
        } else {
            holder.mCheckBoxView.setState(mTags.isChecked(tag) ?
                            CheckBoxTriStates.State.CHECKED :
                            CheckBoxTriStates.State.UNCHECKED);
        }
    }

    private String getTagByIndex(int index) {
        if (mFilteredList == null) {
            return mTags.get(index);
        }
        return mFilteredList.get(index);
    }

    @Override
    public int getItemCount() {
        if (mFilteredList == null) {
            return mTags.size();
        }
        return mFilteredList.size();
    }

    @Override
    public Filter getFilter() {
        return new TagsFilter();
    }

    /* Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026 */
    private class TagsFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            if (constraint.length() == 0) {
                mFilteredList = null;
            } else {
                mFilteredList = new ArrayList<>();
                final String filterPattern = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                for (String tag : mTags) {
                    if (tag.toLowerCase(Locale.getDefault()).contains(filterPattern)) {
                        mFilteredList.add(tag);
                    }
                }
            }

            return FilterResultsUtils.fromCollection(mFilteredList);
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            sortData();
            notifyDataSetChanged();
        }
    }
}
