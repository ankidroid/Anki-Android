/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.CheckResult;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

/** A Dialog displaying The various options for "Help" in a nested structure */
public class RecursivePictureMenu extends DialogFragment {

    public RecursivePictureMenu() {
        // required for a fragment - must be no args
    }

    @CheckResult
    public static RecursivePictureMenu createInstance(ArrayList<Item> itemList) {
        RecursivePictureMenu helpDialog = new RecursivePictureMenu();
        Bundle args = new Bundle();
        args.putParcelableArrayList("bundle", itemList);
        helpDialog.setArguments(args);
        return helpDialog;
    }


    public static void removeFrom(List<Item> allItems, Item toRemove) {
        // Note: currently doesn't remove the top-level elements.
        for (Item i : allItems) {
            i.remove(toRemove);
        }
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        @NonNull
        final List<Item> items = getArguments().getParcelableArrayList("bundle");

        RecyclerView.Adapter<?> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {


            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View root = getLayoutInflater().inflate(R.layout.material_dialog_list_item, parent, false);
                return new RecyclerView.ViewHolder(root) { };
            }


            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                TextView textView = (TextView) holder.itemView;
                Item val = items.get(position);
                textView.setText(val.mText);
                textView.setOnClickListener((l) -> val.execute((AnkiActivity) requireActivity()));
                int mIcon = val.mIcon;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    textView.setCompoundDrawablesRelativeWithIntrinsicBounds(mIcon, 0, 0, 0);
                } else {
                    textView.setCompoundDrawablesWithIntrinsicBounds(mIcon, 0, 0, 0);
                }
            }


            @Override
            public int getItemCount() {
                return items.size();
            }
        };

        MaterialDialog dialog = new MaterialDialog.Builder(requireContext())
                .adapter(adapter, null)
                .show();
        
        View v = dialog.findViewById(R.id.md_contentRecyclerView);
        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 0);
        // DEFECT: There is 9dp of bottom margin which I can't seem to get rid of.

        return dialog;
    }

    public abstract static class Item implements Parcelable {

        private final @StringRes int mText;
        private final @DrawableRes int mIcon;

        public Item(@StringRes int titleString, @DrawableRes int iconDrawable) {
            this.mText = titleString;
            this.mIcon = iconDrawable;
        }

        public List<Item> getChildren() {
            return new ArrayList<>();
        }

        protected Item(Parcel in) {
            mText = in.readInt();
            mIcon = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mText);
            dest.writeInt(mIcon);
        }

        public abstract void execute(AnkiActivity activity);

        public abstract void remove(Item toRemove);
    }

    public static class ItemHeader extends Item implements Parcelable {

        private final List<Item> mChildren;


        public ItemHeader(@StringRes int titleString, int i, Item... children) {
            super(titleString, i);
            mChildren = new ArrayList<>(Arrays.asList(children));
        }


        @Override
        public List<Item> getChildren() {
            return new ArrayList<>(mChildren);
        }


        @Override
        public void execute(AnkiActivity activity) {
            DialogFragment nextFragment = RecursivePictureMenu.createInstance(new ArrayList<>(this.getChildren()));
            activity.showDialogFragment(nextFragment);
        }


        @Override
        public void remove(Item toRemove) {
            mChildren.remove(toRemove);
            for (Item i : mChildren) {
                i.remove(toRemove);
            }
        }


        protected ItemHeader(Parcel in) {
            super(in);
            if (in.readByte() == 0x01) {
                mChildren = new ArrayList<>();
                in.readList(mChildren, Item.class.getClassLoader());
            } else {
                mChildren = new ArrayList<>();
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            if (mChildren == null) {
                dest.writeByte((byte) (0x00));
            } else {
                dest.writeByte((byte) (0x01));
                dest.writeList(mChildren);
            }
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<ItemHeader> CREATOR = new Parcelable.Creator<ItemHeader>() {
            @Override
            public ItemHeader createFromParcel(Parcel in) {
                return new ItemHeader(in);
            }

            @Override
            public ItemHeader[] newArray(int size) {
                return new ItemHeader[size];
            }
        };
    }
}

