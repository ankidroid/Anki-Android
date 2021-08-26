/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                          *
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

package com.ichi2.anki.dialogs;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a reusable convenience class which makes it easy to show a insert field dialog as a DialogFragment.
 * Create a new instance with required fields list, then show it via the fragment manager as usual.
 */
public class InsertFieldDialog extends DialogFragment {

    private MaterialDialog mDialog;
    private List<String> mFieldList;
    private final InsertFieldListener mInsertFieldListener;


    public InsertFieldDialog(@NonNull InsertFieldListener insertFieldListener) {
        this.mInsertFieldListener = insertFieldListener;
    }

    @NonNull
    public InsertFieldDialog withArguments(@NonNull List<String> fieldItems) {
        Bundle args = new Bundle();
        args.putStringArrayList("fieldItems", new ArrayList<>(fieldItems));
        this.setArguments(args);
        return this;
    }

    /**
     * A dialog for inserting field in card template editor
     */
    @NonNull
    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFieldList = getArguments().getStringArrayList("fieldItems");
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
                textView.setText(mFieldList.get(position));
                textView.setOnClickListener((l) -> {
                    selectFieldAndClose(textView);
                });
            }


            @Override
            public int getItemCount() {
                return mFieldList.size();
            }
        };

        mDialog = new MaterialDialog.Builder(requireContext())
                .title(R.string.card_template_editor_select_field)
                .negativeText(R.string.dialog_cancel)
                .adapter(adapter, null)
                .build();

        return mDialog;
    }


    private void selectFieldAndClose(TextView textView) {
        mInsertFieldListener.insertField(textView.getText().toString());
        mDialog.dismiss();
    }

    public interface InsertFieldListener {
        void insertField(String field);
    }

}
