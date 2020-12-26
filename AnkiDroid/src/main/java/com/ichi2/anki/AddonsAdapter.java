package com.ichi2.anki;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class AddonsAdapter extends RecyclerView.Adapter<AddonsAdapter.AddonsViewHolder> {
    private  SharedPreferences preferences;
    private String[] addonsNames;
    public AddonsAdapter(String[] addonsNames) {
        this.addonsNames = addonsNames;
    }

    @NonNull
    @Override
    public AddonsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        preferences = AnkiDroidApp.getSharedPrefs(parent.getContext());
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.addons_list_items, parent, false);
        return new AddonsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AddonsViewHolder holder, int position) {
        String name = addonsNames[position];
        holder.addonsTextView.setText(name);
    }

    @Override
    public int getItemCount() {
        return addonsNames.length;
    }

    public class AddonsViewHolder extends RecyclerView.ViewHolder {
        TextView addonsTextView;
        Switch addonActivate;
        ImageButton addonDelete;
        ImageButton addonInfo;
        public AddonsViewHolder(@NonNull View itemView) {
            super(itemView);
            addonsTextView = (TextView) itemView.findViewById(R.id.addon_name);
            addonActivate = (Switch) itemView.findViewById(R.id.activate_addon);
            addonDelete = (ImageButton) itemView.findViewById(R.id.delete_addon);
            addonInfo = (ImageButton) itemView.findViewById(R.id.addon_info);

            addonActivate.setOnClickListener(v -> {
                SharedPreferences.Editor editor = preferences.edit();
                if (addonActivate.isChecked()) {
                    editor.putString("", "true");
                    editor.apply();
                } else {
                    editor.putString("", "false");
                    editor.apply();
                }
            });
        }
    }
}