import path from "path";
import { uploadI18nFiles } from "./upload";
import { buildAndDownload, extractZip } from "./download";
import { updateI18nFiles } from "./update";

process.argv.forEach(function (value, index, array) {
    switch (value) {
        case "upload":
            // upload latest (english) source i18n files
            console.log("uploadin...");
            uploadI18nFiles();
            break;

        case "download":
            // download build target i18n files
            console.log("downloading...");
            buildAndDownload();
            break;

        case "extract":
            // extract to a temp dir
            console.log("extracting...");
            const temp_dir = path.join(__dirname, "../temp_dir");
            extractZip("ankidroid.zip", temp_dir);
            break;

        case "update":
            // upload to target i18n files
            console.log("updating...");
            updateI18nFiles();
            break;
    }
});
