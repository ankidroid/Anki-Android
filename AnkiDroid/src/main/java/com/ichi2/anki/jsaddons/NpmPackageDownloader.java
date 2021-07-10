package com.ichi2.anki.jsaddons;

import android.content.Context;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.R;
import com.ichi2.async.ProgressSenderAndCancelListener;
import com.ichi2.async.TaskDelegate;
import com.ichi2.libanki.Collection;
import com.ichi2.utils.Computation;

import org.jetbrains.annotations.NotNull;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import java8.util.StringJoiner;
import timber.log.Timber;

import static com.ichi2.anki.web.HttpFetcher.downloadFileToSdCardMethod;
import static com.ichi2.utils.Computation.ERR;
import static com.ichi2.utils.Computation.OK;

public class NpmPackageDownloader {

    public static class DownloadAddon extends TaskDelegate<Void, Computation<?>> {
        private final Context mContext;
        private final DownloadAddonListener mTaskListener;
        private final String addonName;


        public DownloadAddon(Context context, String addonName) {
            this.mContext = context;
            this.addonName = addonName;
            mTaskListener = (DownloadAddonListener) context;
        }

        protected Computation<?> task(@NotNull Collection col, @NotNull ProgressSenderAndCancelListener<Void> collectionTask) {
            mTaskListener.addonShowProgressBar();
            try {

                // mapping for json in http://registry.npmjs.org/ankidroid-js-addon-.../latest
                ObjectMapper mapper = new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                AddonInfo mAddonInfo = mapper.readValue(new URL(mContext.getString(R.string.npmjs_registry, addonName)), AddonInfo.class);

                // check if fields like ankidroidJsApi, addonType exists or not
                if (AddonInfo.isValidAnkiDroidAddon(mAddonInfo)) {
                    String tarballUrl = mAddonInfo.getDist().get("tarball");

                    // download the .tgz file in cache dir of AnkiDroid
                    String downloadFilePath = downloadFileToSdCardMethod(tarballUrl, mContext, "addons", "GET");
                    Timber.d("download path %s", downloadFilePath);

                    // extract the .tgz file to AnkiDroid/addons dir
                    extractAndCopyAddonTgz(downloadFilePath, addonName);
                } else {
                    mTaskListener.addonHideProgressBar();
                    mTaskListener.showToast(mContext.getString(R.string.invalid_js_addon));
                }

                return OK;

            } catch (JsonParseException | JsonMappingException | MalformedURLException e) {
                mTaskListener.showToast(mContext.getString(R.string.invalid_js_addon));
                Timber.w(e.getLocalizedMessage());
            } catch (UnknownHostException e) {
                mTaskListener.showToast(mContext.getString(R.string.network_no_connection));
                Timber.w(e.getLocalizedMessage());
            } catch (NullPointerException | IOException e) {
                mTaskListener.showToast(mContext.getString(R.string.error_occur_downloading_addon));
                Timber.w(e.getLocalizedMessage());
            }

            // if ok not return then progress bar still showing hide progress bar and return error
            mTaskListener.addonHideProgressBar();
            return ERR;
        }


        /**
         * @param tarballPath  path to downloaded js-addon.tgz file
         * @param npmAddonName addon name, e.g ankidroid-js-addon-progress-bar
         *                     extract downloaded .tgz files and copy to AnkiDroid/addons/ folder
         */
        public void extractAndCopyAddonTgz(String tarballPath, String npmAddonName) {
            if (tarballPath == null) {
                mTaskListener.addonHideProgressBar();
                mTaskListener.showToast(mContext.getString(R.string.failed_to_extract_addon_package));
                return;
            }

            String currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(mContext);

            // AnkiDroid/addons/js-addons
            // here npmAddonName is id of npm package which may not contain ../ or other bad path
            StringJoiner joinedPath = new StringJoiner("/")
                    .add(currentAnkiDroidDirectory)
                    .add("addons")
                    .add(npmAddonName);

            File addonsDir = new File(joinedPath.toString());
            File tarballFile = new File(tarballPath);

            if (!tarballFile.exists()) {
                return;
            }

            // extracting using library https://github.com/thrau/jarchivelib
            try {
                Archiver archiver = ArchiverFactory.createArchiver(tarballFile);
                archiver.extract(tarballFile, addonsDir);
                Timber.d("js addon .tgz extracted");
            } catch (IOException e) {
                Timber.e(e.getLocalizedMessage());
            } finally {
                if (tarballFile.exists()) {
                    tarballFile.delete();
                    mTaskListener.addonHideProgressBar();
                    mTaskListener.showToast(mContext.getString(R.string.addon_installed));
                }
            }
        }
    }
}
