/**
 * @author
 * AnkiDroid Open Source Team
 * 
 * @license
 * Copyright (c) AnkiDroid. All rights reserved.
 * Licensed under the GPL-3.0 license. See LICENSE file in the project root for details.
 * 
 * @description
 * For calling functions defined in other files.
 * upload
 *  upload English language source files to crowdin
 * 
 * download
 *  build and download target language files from crowdin 
 * 
 * extract
 *  extract ankidroid.zip to temp_dir
 * 
 * update
 *  copy latest file from temp_dir to AnkiDroid/src/main/res/values/ dir
 */

import { uploadI18nFiles } from "./upload";
import { buildAndDownload, extractZip } from "./download";
import { updateI18nFiles } from "./update";
import { TEMP_DIR } from "./constants";

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
            extractZip("ankidroid.zip", TEMP_DIR);
            break;

        case "update":
            // upload to target i18n files
            console.log("updating...");
            updateI18nFiles();
            break;
    }
});
