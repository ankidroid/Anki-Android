/**
 * @author
 * AnkiDroid Open Source Team
 *
 * @license
 * Copyright (c) AnkiDroid. All rights reserved.
 * Licensed under the GPL-3.0 license. See LICENSE file in the project root for details.
 *
 * @description
 * updateI18nFiles() to update files in AnkiDroid/src/main/res/values/ with downloaded files from crowdin.
 * The downloaded file needs to extract first with 'yarn start extract'.
 * It's expected to be called through 'yarn start update'.
 */

import fs from "fs";
import path from "path";
import readline from "readline";
import {
    LANGUAGES,
    LOCALIZED_REGIONS,
    TEMP_DIR,
    TITLE_STR,
    TITLE_FILE,
    I18N_FILES,
    XML_LICENSE_HEADER,
    RES_VALUES_LANG_DIR,
    OLD_VER_MARKET_DESC_FILE,
    MARKET_DESC_LANG,
} from "./constants";

let anyError = false;

/**
 * Replace invalid chars in xml files in res/value dir
 * e.g. %1s$ is invalid, %1$s is valid
 *
 * @param fileName name of the file in res/value dir
 * @returns boolean true if any corrections were made, false if no corrections were needed
 */
async function replacechars(fileName: string): Promise<boolean> {
    const newfilename = fileName + ".tmp";

    const fileStream = fs.createReadStream(fileName);

    const rl = readline.createInterface({
        input: fileStream,
        crlfDelay: Infinity,
    });

    for await (let line of rl) {
        if (line.startsWith("<?xml")) {
            line = XML_LICENSE_HEADER;
        } else {
            // remove space before </item> and add new lines to after </item>
            if (line.startsWith("    <item>0 </item>")) {
                line = "    <item>0</item>\n";
            }

            // running prettier will change this line, remove back slash
            line = line.replace(/\'/g, "\\'");
            line = line.replace(/\\\\\'/g, "\\'");
            line = line.replace(/\n\s/g, "\\n");
            line = line.replace(/…/g, "&#8230;");
        }

        fs.appendFileSync(newfilename, line + "\n");
    }

    fs.rename(newfilename, fileName, function (err) {
        if (err) throw err;
        console.log(`File ${fileName} successfully copied`);
    });

    return true;
}

/**
 * Get file extension for the file
 *
 * @param f filename
 * @returns extension string
 */
function fileExtFor(f: string): string {
    if (f == "14-marketdescription") return ".txt";
    else if (f == "15-markettitle") return ".txt";
    else return ".xml";
}

/**
 * Create language resource directory in res/value dir
 *
 * @param directory name of the directory
 */
export function createDirIfNotExisting(directory: string) {
    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory);
    }
}

/**
 * For existing directory update xml files in res/value dir for each languages
 *
 * @param valuesDirectory res/value dir for the language
 * @param translatedContent content of target language xml file
 * @param f txt, xml file name
 * @param fileExt extension of the file
 * @param language language code
 * @returns boolean for successfully replaced invalid string and copied updated files
 */
async function update(
    valuesDirectory: string,
    translatedContent: string,
    f: string,
    fileExt: string,
    language = "",
): Promise<boolean> {
    if (f == "14-marketdescription") {
        const newfile = path.join(MARKET_DESC_LANG + language + fileExt);

        fs.writeFileSync(newfile, translatedContent);

        const oldContent = fs.readFileSync(OLD_VER_MARKET_DESC_FILE).toString();
        const newContent = fs.readFileSync(newfile).toString();

        for (let i = 0; i < oldContent.length; i++) {
            if (oldContent[i] != newContent[i]) {
                console.log("File " + newfile + " successfully copied");
                return true;
            }
        }

        fs.unlinkSync(newfile);
        console.log(
            "File marketdescription is not translated into language " + language,
        );
        return true;
    } else if (f == "15-markettitle") {
        const translatedTitle = translatedContent.split("\n")[0];

        if (TITLE_STR != translatedTitle) {
            fs.appendFileSync(TITLE_FILE, "\n" + language + ": " + translatedTitle);
            console.log("Added translated title");
        } else {
            console.log("Title not translated");
        }
        return true;
    } else {
        const newfile = valuesDirectory + f + ".xml";
        fs.writeFileSync(newfile, translatedContent);
        return replacechars(newfile);
    }
}

/**
 * Update translated I18n files in res/value dir
 */
export async function updateI18nFiles() {
    for (const language of LANGUAGES) {
        // Regional files need a marker in Android
        // https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources -
        // The language is defined by a two-letter ISO 639-1 language code, optionally followed by a two letter ISO 3166-1-alpha-2 region code (preceded by lowercase r).
        //
        // The codes are not case-sensitive; the r prefix is used to distinguish the region portion. You cannot specify a region alone.
        let androidLanguage = "";
        const languageCode = language.split("-", 1)[0];
        if (LOCALIZED_REGIONS.includes(languageCode)) {
            androidLanguage = language.replace("-", "-r"); // zh-CW becomes zh-rCW
        } else {
            androidLanguage = language.split("-", 1)[0]; // Example: es-ES becomes es
        }

        switch (language) {
            case "yu":
                androidLanguage = "yue";
                break;

            case "he":
                androidLanguage = "heb";
                break;

            case "id":
                androidLanguage = "ind";
                break;

            case "tl":
                androidLanguage = "tgl";
                break;
        }

        console.log(
            "\nCopying language files from " + language + " to " + androidLanguage,
        );
        const valuesDirectory = path.join(RES_VALUES_LANG_DIR + androidLanguage + "/");
        createDirIfNotExisting(valuesDirectory);

        // Copy localization files, mask chars and append gnu/gpl licence
        for (const f of I18N_FILES) {
            const fileExt = fileExtFor(f);
            const translatedContent = fs.readFileSync(
                TEMP_DIR + "/" + language + "/" + f + fileExt,
                "utf-8",
            );
            anyError = !(await update(
                valuesDirectory,
                translatedContent,
                f,
                fileExt,
                language,
            ));
        }

        if (anyError) {
            console.error(
                "At least one file of the last handled language contains an error.",
            );
            anyError = true;
        }
    }
}
