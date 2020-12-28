package com.ichi2.anki;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class AddonsAdapter extends RecyclerView.Adapter<AddonsAdapter.AddonsViewHolder> {
    private  SharedPreferences preferences;
    private Context context;
    List<AddonModel> addonModels;
    public AddonsAdapter(List<AddonModel> addonModels) {
        this.addonModels = addonModels;
    }

    @NonNull
    @Override
    public AddonsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        preferences = AnkiDroidApp.getSharedPrefs(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.addons_list_items, parent, false);
        return new AddonsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AddonsViewHolder holder, int position) {
        AddonModel addonModel = addonModels.get(position);
        holder.addonsTextView.setText(addonModel.getName());

        // while binding viewholder if preferences w.r.t viewholder store true value or enabled status then
        // turn on switch status else it is off by default
        if (preferences.getString("addon:"+addonModel.getId(), "disabled").equals("enabled")) {
            holder.addonActivate.setChecked(true);
        }

        holder.addonActivate.setOnClickListener(v -> {
            SharedPreferences.Editor editor = preferences.edit();

            // store enabled/disabled status as boolean true/false value in SharedPreferences
            if (holder.addonActivate.isChecked()) {
                editor.putString("addon:"+addonModel.getId(), "enabled");
                editor.apply();
                UIUtils.showThemedToast(context, context.getString(R.string.enabled)+" "+addonModel.getName(), true);
            } else {
                editor.putString("addon:"+addonModel.getId(), "disabled");
                editor.apply();
                UIUtils.showThemedToast(context, context.getString(R.string.disabled)+" "+addonModel.getName(), true);
            }
        });

        Dialog infoDialog = new Dialog(context);
        infoDialog.setCanceledOnTouchOutside(true);
        infoDialog.setContentView(R.layout.addon_info_popup);
        holder.addonInfo.setOnClickListener(v -> {
            TextView id = infoDialog.findViewById(R.id.popup_addon_id_info);
            TextView name = infoDialog.findViewById(R.id.popup_addon_name_info);
            TextView ver = infoDialog.findViewById(R.id.popup_addon_version_info);
            TextView dev = infoDialog.findViewById(R.id.popup_addon_dev_info);
            TextView ankidroid_api = infoDialog.findViewById(R.id.popup_ankidroid_api_info);

            id.setText(addonModel.getId());
            name.setText(addonModel.getName());
            ver.setText(addonModel.getVersion());
            dev.setText(addonModel.getDeveloper());
            ankidroid_api.setText(addonModel.getAnkidroid_api());

            infoDialog.show();
        });

        holder.addonDelete.setOnClickListener(v -> {
            // Confirm delete, remove file and folder of respective addon
            // TO-DO
        });
    }

    @Override
    public int getItemCount() {
        return addonModels.size();
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
        }
    }
}