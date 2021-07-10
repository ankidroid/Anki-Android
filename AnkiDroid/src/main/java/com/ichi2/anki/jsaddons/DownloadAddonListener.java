package com.ichi2.anki.jsaddons;

public interface DownloadAddonListener {
    void listAddonsFromDir(String addonType);
    void addonShowProgressBar();
    void addonHideProgressBar();
    void showToast(String msg);
}
