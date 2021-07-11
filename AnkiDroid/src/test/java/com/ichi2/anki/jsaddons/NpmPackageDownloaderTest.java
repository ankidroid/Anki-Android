package com.ichi2.anki.jsaddons;

import android.content.Context;

import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.RunInBackground;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class NpmPackageDownloaderTest extends RobolectricTest {
    private final String inValidAddonName = "fs";
    private final String validAddonName = "ankidroid-js-addon-progress-bar";


    @Test
    @RunInBackground
    public void validAddonTest() throws ExecutionException, InterruptedException {
        final Context context = getTargetContext();
        CollectionTask<Void, String> ct = TaskManager.launchCollectionTask(new NpmPackageDownloader.DownloadAddon(context, validAddonName));

        // this string is toast when addon successfully installed
        // launchCollectionTask return, 'JavaScript Addon installed'
        assertEquals(context.getString(R.string.addon_installed), ct.get());
    }


    @Test
    @RunInBackground
    public void inValidAddonTest() throws ExecutionException, InterruptedException {
        final Context context = getTargetContext();
        CollectionTask<Void, String> ct = TaskManager.launchCollectionTask(new NpmPackageDownloader.DownloadAddon(context, inValidAddonName));

        // this string is toast when not valid addon requested
        // launchCollectionTask return, 'Not a valid js addons package for AnkiDroid'
        assertEquals(context.getString(R.string.invalid_js_addon), ct.get());
    }
}
