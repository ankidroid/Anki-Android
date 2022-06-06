import fs from "fs";
import axios from "axios";
import crowdin, { Credentials } from '@crowdin/crowdin-api-client';

const extract = require('extract-zip')

require('dotenv').config();

const CROWDIN_API_KEY = process.env.CROWDIN_API_KEY ?? "";
const projectId = 520224;

// credentials
const credentials: Credentials = {
    token: CROWDIN_API_KEY
};

// initialization of crowdin client
const {
    translationsApi
} = new crowdin(credentials);

export async function buildAndDownload() {
    try {
        // build
        console.log("Building ZIP on server...");
        const buildId = await translationsApi.buildProject(projectId);
        console.log("Built.");

        // download
        console.log("Downloading Crowdin file");
        const downloadLink = await translationsApi.downloadTranslations(projectId, buildId.data.id);
        axios({
            method: "get",
            url: downloadLink.data.url,
            responseType: "stream"
        }).then(function (response) {
            response.data.pipe(fs.createWriteStream("ankidroid.zip"));
        });

    } catch (error) {
        console.log(error);
    }
}

export async function extractZip(source: string, target: string) {
    try {
        await extract(source, { dir: target });
        console.log('Extraction complete')
      } catch (err) {
        console.log(err);
      }
}
