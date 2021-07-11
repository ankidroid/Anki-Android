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

public class NpmPackageDownloader {

    public static class DownloadAddon extends TaskDelegate<Void, String> {
        private final Context mContext;
        private final String addonName;


        public DownloadAddon(Context context, String addonName) {
            this.mContext = context;
            this.addonName = addonName;
        }


        protected String task(@NotNull Collection col, @NotNull ProgressSenderAndCancelListener<Void> psacl) {
            try {

                // mapping for json in http://registry.npmjs.org/ankidroid-js-addon-.../latest
                ObjectMapper mapper = new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                AddonInfo mAddonInfo = mapper.readValue(new URL(mContext.getString(R.string.npmjs_registry, addonName)), AddonInfo.class);

                // check if fields like ankidroidJsApi, addonType exists or not
                if (!AddonInfo.isValidAnkiDroidAddon(mAddonInfo)) {
                    return mContext.getString(R.string.invalid_js_addon);
                }

                String tarballUrl = mAddonInfo.getDist().get("tarball");
                String addonType = mAddonInfo.getAddonType();

                // download the .tgz file in cache dir of AnkiDroid
                String downloadFilePath = downloadFileToSdCardMethod(tarballUrl, mContext, "addons", "GET");
                Timber.d("download path %s", downloadFilePath);

                // extract the .tgz file to AnkiDroid/addons dir
                boolean extracted = extractAndCopyAddonTgz(downloadFilePath, addonName);
                if (!extracted) {
                    return mContext.getString(R.string.failed_to_extract_addon_package);
                }
                // addonType sent to list the addons in recycler view
                return mContext.getString(R.string.addon_installed);

            } catch (JsonParseException | JsonMappingException | MalformedURLException e) {
                Timber.w(e.getLocalizedMessage());
                return mContext.getString(R.string.invalid_js_addon);
            } catch (UnknownHostException e) {
                Timber.w(e.getLocalizedMessage());
                return mContext.getString(R.string.network_no_connection);
            } catch (NullPointerException | IOException e) {
                Timber.w(e.getLocalizedMessage());
                return mContext.getString(R.string.error_occur_downloading_addon);
            }
        }


        public boolean extractAndCopyAddonTgz(String tarballPath, String npmAddonName) {
            if (tarballPath == null) {
                return false;
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
                return false;
            }

            // extracting using library https://github.com/thrau/jarchivelib
            try {
                Archiver archiver = ArchiverFactory.createArchiver(tarballFile);
                archiver.extract(tarballFile, addonsDir);
                Timber.d("js addon .tgz extracted");
            } catch (IOException e) {
                Timber.e(e.getLocalizedMessage());
                return false;
            } finally {
                if (tarballFile.exists()) {
                    tarballFile.delete();
                }
            }
            return true;
        }
    }
}
