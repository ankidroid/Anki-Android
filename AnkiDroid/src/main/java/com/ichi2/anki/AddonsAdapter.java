package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

public class AddonsAdapter extends RecyclerView.Adapter<AddonsAdapter.AddonsViewHolder> {
    private SharedPreferences preferences;
    private Context context;
    List<AddonModel> addonModels;
    private OnAddonClickListener mOnAddonClickListener;


    public AddonsAdapter(List<AddonModel> addonModels, OnAddonClickListener mOnAddonClickListener) {
        this.addonModels = addonModels;
        this.mOnAddonClickListener = mOnAddonClickListener;
    }


    @NonNull
    @Override
    public AddonsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        preferences = AnkiDroidApp.getSharedPrefs(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.addons_list_items, parent, false);
        return new AddonsViewHolder(view, mOnAddonClickListener);
    }


    @Override
    public void onBindViewHolder(@NonNull AddonsViewHolder holder, int position) {
        AddonModel addonModel = addonModels.get(position);
        holder.addonsTextView.setText(addonModel.getName());

        // while binding viewholder if preferences w.r.t viewholder store true value or enabled status then
        // turn on switch status else it is off by default

        String addonTypeAndName = AddonModel.getAddonFullName(addonModel);

        if (preferences.getBoolean(addonTypeAndName, false)) {
            holder.addonActivate.setChecked(true);
        }

        holder.addonActivate.setOnClickListener(v -> {
            SharedPreferences.Editor editor = preferences.edit();
            // store enabled/disabled status as boolean true/false value in SharedPreferences

            if (holder.addonActivate.isChecked()) {
                editor.putBoolean(addonTypeAndName, true).apply();
                UIUtils.showThemedToast(context, context.getString(R.string.addon_enabled, addonModel.getName()), true);
            } else {
                editor.putBoolean(addonTypeAndName, false).apply();
                UIUtils.showThemedToast(context, context.getString(R.string.addon_disabled, addonModel.getName()), true);
            }
        });

    }


    @Override
    public int getItemCount() {
        return addonModels.size();
    }


    public class AddonsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView addonsTextView;
        SwitchCompat addonActivate;
        OnAddonClickListener onAddonClickListener;


        public AddonsViewHolder(@NonNull View itemView, OnAddonClickListener onAddonClickListener) {
            super(itemView);
            addonsTextView = itemView.findViewById(R.id.addon_name);
            addonActivate = itemView.findViewById(R.id.activate_addon);
            this.onAddonClickListener = onAddonClickListener;
            itemView.setOnClickListener(this);
        }


        @Override
        public void onClick(View v) {
            onAddonClickListener.onAddonClick(getAdapterPosition());
        }
    }


    public interface OnAddonClickListener {
        void onAddonClick(int position);
    }

}