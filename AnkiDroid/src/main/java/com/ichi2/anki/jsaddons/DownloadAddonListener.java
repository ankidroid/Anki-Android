package com.ichi2.anki.jsaddons;

import android.content.Context;

import com.ichi2.anki.AddonBrowser;
import com.ichi2.anki.UIUtils;
import com.ichi2.async.TaskListener;

public class DownloadAddonListener extends TaskListener<Void, String> {
    private Context mContext;
    private AddonBrowser mAddonBrowser;

    public DownloadAddonListener(AddonBrowser addonBrowser) {
        this.mContext = addonBrowser.getBaseContext();
        this.mAddonBrowser = addonBrowser;
    }


    @Override
    public void onPreExecute() {
        mAddonBrowser.showProgressBar();
    }


    @Override
    public void onPostExecute(String s) {
        UIUtils.showThemedToast(mContext, s, false);
        mAddonBrowser.listAddonsFromDir();
        mAddonBrowser.hideProgressBar();
    }

}
