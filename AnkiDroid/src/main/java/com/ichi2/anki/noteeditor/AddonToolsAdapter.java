package com.ichi2.anki.noteeditor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import com.ichi2.anki.AddonModel;
import com.ichi2.anki.AddonsAdapter;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class AddonToolsAdapter extends RecyclerView.Adapter<AddonToolsAdapter.AddonToolsViewHolder> {
    private Context mContext;
    List<AddonToolsModel> addonToolsModelList;
    private OnAddonClickListener mOnAddonClickListener;
    public AddonToolsAdapter(List<AddonToolsModel> addonToolsModelList, AddonToolsAdapter.OnAddonClickListener mOnAddonClickListener) {
        this.addonToolsModelList = addonToolsModelList;
        this.mOnAddonClickListener = mOnAddonClickListener;
    }



    @NonNull
    @Override
    public AddonToolsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        mContext = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.addon_tool, parent, false);
        return new AddonToolsAdapter.AddonToolsViewHolder(view, mOnAddonClickListener);
    }


    @Override
    public void onBindViewHolder(@NonNull AddonToolsViewHolder holder, int position) {
        AddonToolsModel addonToolsModel = addonToolsModelList.get(position);
        holder.addonBtn.setText(addonToolsModel.getIcon().toString());
    }


    @Override
    public int getItemCount() {
        return addonToolsModelList.size();
    }


    public class AddonToolsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        TextView addonBtn;
        AddonToolsAdapter.OnAddonClickListener onAddonClickListener;
        public AddonToolsViewHolder(@NonNull View itemView, AddonToolsAdapter.OnAddonClickListener onAddonClickListener) {
            super(itemView);
            addonBtn = itemView.findViewById(R.id.addon_btn);
            this.onAddonClickListener = onAddonClickListener;
            //itemView.setOnClickListener(this);
            addonBtn.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            onAddonClickListener.onAddonClick(getAdapterPosition());
        }
    }

    public interface OnAddonClickListener{
        void onAddonClick(int position);
    }
}
