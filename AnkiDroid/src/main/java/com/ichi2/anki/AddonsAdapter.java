package com.ichi2.anki;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
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
        if (preferences.getString("addon:"+addonModel.getName(), "disabled").equals("enabled")) {
            holder.addonActivate.setChecked(true);
        }

        holder.addonActivate.setOnClickListener(v -> {
            SharedPreferences.Editor editor = preferences.edit();

            // store enabled/disabled status as boolean true/false value in SharedPreferences
            if (holder.addonActivate.isChecked()) {
                editor.putString("addon:"+addonModel.getName(), "enabled");
                editor.apply();
                UIUtils.showThemedToast(context, context.getString(R.string.enabled)+" "+addonModel.getName(), true);
            } else {
                editor.putString("addon:"+addonModel.getName(), "disabled");
                editor.apply();
                UIUtils.showThemedToast(context, context.getString(R.string.disabled)+" "+addonModel.getName(), true);
            }
        });

        Dialog infoDialog = new Dialog(context);
        infoDialog.setCanceledOnTouchOutside(true);
        infoDialog.setContentView(R.layout.addon_info_popup);
        holder.addonInfo.setOnClickListener(v -> {
            TextView name = infoDialog.findViewById(R.id.popup_addon_name_info);
            TextView ver = infoDialog.findViewById(R.id.popup_addon_version_info);
            TextView dev = infoDialog.findViewById(R.id.popup_addon_dev_info);
            TextView ankidroid_api = infoDialog.findViewById(R.id.popup_ankidroid_api_info);
            TextView homepage = infoDialog.findViewById(R.id.popup_addon_homepage_info);

            name.setText(addonModel.getName());
            ver.setText(addonModel.getVersion());
            dev.setText(addonModel.getDeveloper());
            ankidroid_api.setText(addonModel.getAnkidroid_api());

            String link = "<a href='" + addonModel.getHomepage() + "'>" + addonModel.getHomepage() + "</a>";
            homepage.setClickable(true);
            homepage.setText(HtmlCompat.fromHtml(link, HtmlCompat.FROM_HTML_MODE_LEGACY));

            infoDialog.show();
        });

        holder.addonDelete.setOnClickListener(v -> {

            String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(context);
            File addonsDir = new File(currentAnkiDroidDirectory, "addons" );

            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
            alertBuilder.setTitle(addonModel.getName());
            alertBuilder.setMessage(context.getString(R.string.confirm_remove_addon, addonModel.getName()));
            alertBuilder.setCancelable(true);

            alertBuilder.setPositiveButton(
                    context.getString(R.string.dialog_ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // remove the js addon folder
                            File dir = new File(addonsDir, addonModel.getName());
                            deleteDirectory(dir);

                            // remove enabled status
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.remove("addon:"+addonModel.getName());
                            editor.apply();

                            addonModels.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, addonModels.size());
                            notifyDataSetChanged();
                        }
                    });

            alertBuilder.setNegativeButton(
                    context.getString(R.string.dialog_cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });

            AlertDialog deleteAlert = alertBuilder.create();
            deleteAlert.show();

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

    public static void deleteDirectory(File dir) {
        if ( dir.isDirectory() ) {
            String [] children = dir.list();
            for ( int i = 0 ; i < children.length ; i ++ ) {
                File child = new File( dir , children[i] );

                if (child.isDirectory()){
                    deleteDirectory(child);
                    child.delete();
                } else {
                    child.delete();
                }
            }

            dir.delete();
        }
    }
}