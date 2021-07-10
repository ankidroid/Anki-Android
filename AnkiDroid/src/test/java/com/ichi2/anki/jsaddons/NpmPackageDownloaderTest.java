package com.ichi2.anki.jsaddons;

import android.content.Context;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.RunInBackground;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskManager;
import com.ichi2.utils.Computation;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
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
        CollectionTask<Void, Computation<?>> task  = TaskManager.launchCollectionTask(new NpmPackageDownloader.DownloadAddon(context, validAddonName));
        task.get();
    }


    @Test
    public void addonDownloadTest() {

    }
}
