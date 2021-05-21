package com.ichi2.anki;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

public class AddonsAdapter extends RecyclerView.Adapter<AddonsAdapter.AddonsViewHolder> {
    private SharedPreferences mPreferences;
    private Context mContext;
    List<AddonModel> mAddonModels;
    private OnAddonClickListener mOnAddonClickListener;


    public AddonsAdapter(List<AddonModel> addonModels, OnAddonClickListener mOnAddonClickListener) {
        this.mAddonModels = addonModels;
        this.mOnAddonClickListener = mOnAddonClickListener;
    }


    @NonNull
    @Override
    public AddonsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mContext = parent.getContext();
        mPreferences = AnkiDroidApp.getSharedPrefs(mContext);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.addons_list_items, parent, false);
        return new AddonsViewHolder(view, mOnAddonClickListener);
    }


    @Override
    public void onBindViewHolder(@NonNull AddonsViewHolder holder, int position) {
        AddonModel addonModel = mAddonModels.get(position);
        holder.mAddonsTextView.setText(addonModel.getName());

        // while binding viewholder if preferences w.r.t viewholder store true value or enabled status then
        // turn on switch status else it is off by default

        // store enabled/disabled status as boolean true/false value in SharedPreferences
        String reviewerAddonKey = AddonModel.getReviewerAddonKey();
        Set<String> reviewerEnabledAddonSet = mPreferences.getStringSet(reviewerAddonKey, new HashSet<String>());

        for (String s : reviewerEnabledAddonSet) {
            if (s.equals(addonModel.getName())) {
                holder.mAddonActivate.setChecked(true);
            }
        }

        holder.mAddonActivate.setOnClickListener(v -> {
            if (holder.mAddonActivate.isChecked()) {
                addonModel.updatePrefs(mPreferences, reviewerAddonKey, addonModel.getName(), false);
                UIUtils.showThemedToast(mContext, mContext.getString(R.string.addon_enabled, addonModel.getName()), true);
            } else {
                addonModel.updatePrefs(mPreferences, reviewerAddonKey, addonModel.getName(), true);
                UIUtils.showThemedToast(mContext, mContext.getString(R.string.addon_disabled, addonModel.getName()), true);
            }

        });

    }


    @Override
    public int getItemCount() {
        return mAddonModels.size();
    }


    public class AddonsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView mAddonsTextView;
        SwitchCompat mAddonActivate;
        OnAddonClickListener mAddonClickListener;


        public AddonsViewHolder(@NonNull View itemView, OnAddonClickListener onAddonClickListener) {
            super(itemView);
            mAddonsTextView = itemView.findViewById(R.id.addon_name);
            mAddonActivate = itemView.findViewById(R.id.activate_addon);
            this.mAddonClickListener = onAddonClickListener;
            itemView.setOnClickListener(this);
        }


        @Override
        public void onClick(View v) {
            mAddonClickListener.onAddonClick(getLayoutPosition());
        }
    }



    public interface OnAddonClickListener {
        void onAddonClick(int position);
    }
}