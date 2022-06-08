/**
 * @author
 * AnkiDroid Open Source Team
 * 
 * @license
 * Copyright (c) AnkiDroid. All rights reserved.
 * Licensed under the GPL-3.0 license. See LICENSE file in the project root for details.
 * 
 * @description
 * buildAndDownload() will build zip file on Crowdin server and download it as ankidroid.zip file.
 * It's expected to be called through 'yarn start download'.
 * 
 * extractZip() will extract downloaded ankidroid.zip file.
 * It's expected to be called through 'yarn start extract'.
 */

import fs from "fs";
import axios from "axios";
import crowdin from '@crowdin/crowdin-api-client';
import { PROJECT_ID, credentialsConst } from "./constants";

const extract = require('extract-zip');

// initialization of crowdin client
const {
    translationsApi
} = new crowdin(credentialsConst);

/**
 * Build ankidroid.zip file on Crowdin server and download it
 */
export async function buildAndDownload() {
    try {
        // build
        console.log("Building ZIP on server...");
        const buildId = await translationsApi.buildProject(PROJECT_ID);
        console.log("Built.");

        // download
        console.log("Downloading Crowdin file");
        const downloadLink = await translationsApi.downloadTranslations(PROJECT_ID, buildId.data.id);
        axios({
            method: "get",
            url: downloadLink.data.url,
            responseType: "stream"
        }).then(function (response) {
            response.data.pipe(fs.createWriteStream("ankidroid.zip"));
        });

    } catch (error) {
        console.error(error);
    }
}

/**
 * Extract the ankidroid.zip file to temp dir
 * @param source ankidroid.zip file path
 * @param target extract dir path
 */
export async function extractZip(source: string, target: string) {
    try {
        await extract(source, { dir: target });
        console.log('Extraction complete')
      } catch (err) {
        console.log(err);
      }
}
