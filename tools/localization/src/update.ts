import fs from 'fs';
import path from "path";
import readline from 'readline';


const I18N_FILE_BASE = "../../AnkiDroid/src/main/res/values/"

const languages = ['af', 'am', 'ar', 'az', 'be', 'bg', 'bn', 'ca', 'ckb', 'cs', 'da', 'de', 'el', 'eo',
    'es-AR', 'es-ES', 'et', 'eu', 'fa', 'fi', 'fil', 'fr', 'fy-NL', 'ga-IE', 'gl', 'got',
    'gu-IN', 'he', 'hi', 'hr', 'hu', 'hy-AM', 'id', 'is', 'it', 'ja', 'jv', 'ka', 'kk',
    'km', 'kn', 'ko', 'ku', 'ky', 'lt', 'lv', 'mk', 'ml-IN', 'mn', 'mr', 'ms', 'my', 'nl', 'nn-NO', 'no',
    'or', 'pa-IN', 'pl', 'pt-BR', 'pt-PT', 'ro', 'ru', 'sat', 'sc', 'sk', 'sl', 'sq', 'sr', 'ss', 'sv-SE',
    'sw', 'ta', 'te', 'tg', 'th', 'ti', 'tl', 'tn', 'tr', 'ts', 'tt-RU', 'uk', 'ur-PK',
    'uz', 've', 'vi', 'wo', 'xh', 'yu', 'zh-CN', 'zh-TW', 'zu'];

const localizedRegions = ['es', 'pt', 'zh'];
const fileNames = ['01-core', '02-strings', '03-dialogs', '04-network', '05-feedback', '06-statistics', '07-cardbrowser', '08-widget', '09-backup', '10-preferences', '11-arrays', '14-marketdescription', '15-markettitle', '16-multimedia-editor', '17-model-manager', '18-standard-models'];
const titleFile = '../../docs/marketing/localized_description/ankidroid-titles.txt';
const titleString = 'AnkiDroid Flashcards';
let anyError = false;

const temp_dir = path.join(__dirname, "../temp_dir");

async function replacechars(fileName: string, fileExt: string, isCrowdin: boolean) {
    let errorOccured = false;
    let newfilename = fileName + ".tmp"

    const fileStream = fs.createReadStream(fileName);

    const rl = readline.createInterface({
        input: fileStream,
        crlfDelay: Infinity
    });

    if (fileExt != ".csv") {
        for await (let line of rl) {
            if (line.startsWith("<?xml")) {
                line = "<?xml version=\"1.0\" encoding=\"utf-8\"?> \n <!--\n ~ THIS IS AN AUTOMATICALLY GENERATED FILE. PLEASE DO NOT EDIT THIS FILE. \n ~ 1. If you would like to add/delete/modify the original translatable strings, follow instructions here:  https://github.com/ankidroid/Anki-Android/wiki/Development-Guide#adding-translations  \n ~ 2. If you would like to provide a translation of the original file, you may do so using Crowdin. \n ~    Instructions for this are available here: https://github.com/ankidroid/Anki-Android/wiki/Translating-AnkiDroid. \n ~    You may also find the documentation on contributing to Anki useful: https://github.com/ankidroid/Anki-Android/wiki/Contributing   \n ~ \n ~ Copyright (c) 2009 Andrew <andrewdubya@gmail> \n ~ Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com> \n ~ Copyright (c) 2009 Daniel Svaerd <daniel.svard@gmail.com> \n ~ Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com> \n ~ Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com> \n ~ This program is free software; you can redistribute it and/or modify it under \n ~ the terms of the GNU General Public License as published by the Free Software \n ~ Foundation; either version 3 of the License, or (at your option) any later \n ~ version. \n ~ \n ~ This program is distributed in the hope that it will be useful, but WITHOUT ANY \n ~ WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A \n ~ PARTICULAR PURPOSE. See the GNU General Public License for more details. \n ~ \n ~ You should have received a copy of the GNU General Public License along with \n ~ this program.  If not, see <http://www.gnu.org/licenses/>. \n -->\n";
            } else {
                if (line.startsWith("    <item>0 </item>")) {
                    line = "    <item>0</item>\n";
                }

                line = line.replace(/\'/g, '\\\'');
                line = line.replace(/\\\\\'/g, '\\\'');
                line = line.replace(/\n\s/g, '\\n');
                line = line.replace(/…/g, "&#8230;");
                // line = line.replaceAll('amp;', '');

                let regexp = new RegExp(/%[0-9]s\$/g);
                if (line.search(regexp) != -1) {
                    errorOccured = true;
                }
            }

            // console.log(line);
            fs.appendFileSync(newfilename, line + "\n");
        }

    } else {
        fs.appendFileSync(newfilename, "<?xml version=\"1.0\" encoding=\"utf-8\"?> \n <!-- \n ~ Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com> \n ~ This program is free software; you can redistribute it and/or modify it under \n ~ the terms of the GNU General Public License as published by the Free Software \n ~ Foundation; either version 3 of the License, or (at your option) any later \n ~ version. \n ~ \n ~ This program is distributed in the hope that it will be useful, but WITHOUT ANY \n ~ WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A \n ~ PARTICULAR PURPOSE. See the GNU General Public License for more details. \n ~ \n ~ You should have received a copy of the GNU General Public License along with \n ~ this program.  If not, see <http://www.gnu.org/licenses/>. \n --> \n \n \n<resources> \n <string-array name=\"tutorial_questions\"> \n");

        const buffer = fs.readFileSync(fileName);
        const fileContent = buffer.toString();

        let contents = fileContent.replace('([^\"])\n', "\\1").split("\n");
        let line = [];
        let start = 0;

        for (let content of contents) {
            if (isCrowdin) {
                start = content.search('\",\"') + 3;
            } else {
                start = content.search('\"') + 1;
            }

            let contentLine = content.substring(start, content.length - 1);
            let setPos = contentLine.search('<separator>');

            if (setPos == -1) {
                if (contentLine.length > 2) {
                    errorOccured = true;
                    console.log(contentLine);
                }
                continue;
            }

            line.push(["<![CDATA[" + contentLine.substring(0, setPos) + "]]>", "<![CDATA[" + contentLine.substring(setPos + 11, contentLine.length - 1) + "]]>"]);
        }

        for (let fi of line) {
            fi[0] = fi[0].replace('\"+', '\\\"');
            fi[0] = fi[0].replace('\'+', '\\\'');
            fi[0] = fi[0].replace('\\\\{2,}', '\\\\');
            fs.appendFileSync(newfilename, "    <item>" + fi[0] + "</item> \n");
        }

        fs.appendFileSync(newfilename, " </string-array>\n <string-array name=\"tutorial_answers\">\n");

        for (let fi of line) {
            fi[1] = fi[1].replace('\"+', '\\\"');
            fi[1] = fi[1].replace('\'+', '\\\'');
            fi[1] = fi[1].replace('\\\\{2,}', '\\\\');
            fs.appendFileSync(newfilename, "    <item>" + fi[1] + "</item> \n");
        }

        fs.appendFileSync(newfilename, " </string-array>\n</resources>");
    }

    fs.rename(newfilename, fileName, function (err) {
        if (err) throw err
        console.log('Successfully renamed!')
    })

    if (errorOccured) {
        console.log('Error in file ' + fileName)
        return false;
    }

    return true;
}

function fileExtFor(f: string) {
    if (f == '14-marketdescription') return '.txt';
    else if (f == '15-markettitle') return '.txt';
    else return '.xml';
}

function createIfNotExisting(directory: string) {
    if (!fs.existsSync(directory)) {
        fs.mkdirSync(directory);
    }
}

async function update(valuesDirectory: string, f: string, fileExt: string, isCrowdin: boolean, language = '') {
    if (f == '14-marketdescription') {
        let source = fs.readFileSync(temp_dir + "/" + language + "/" + f + fileExt, "utf-8");

        let olfFile = path.join(__dirname, '../../../docs/marketing/localized_description/oldVersionJustToCompareWith.txt');
        let newfile = path.join(__dirname, '../../../docs/marketing/localized_description/marketdescription' + '-' + language + fileExt);

        fs.writeFileSync(newfile, source);

        const oldContent = fs.readFileSync(olfFile).toString();
        const newContent = fs.readFileSync(newfile).toString();

        for (let i = 0; i < oldContent.length; i++) {
            if (oldContent[i] != newContent[i]) {
                console.log('File ' + newfile + ' successfully copied');
                return true;
            }
        }

        fs.unlinkSync(newfile);
        console.log('File marketdescription is not translated into language ' + language);
        return true;

    } else if (f == '15-markettitle') {
        let source = fs.readFileSync(temp_dir + "/" + language + "/14-marketdescription" + fileExt, "utf-8");

        const translatedTitle = source.split("\n")[0];

        if (titleString != translatedTitle) {
            fs.appendFileSync(titleFile, "\n" + language + ': ' + translatedTitle);
            console.log('Added translated title');
        } else {
            console.log('Title not translated');
        }
        return true;

    } else {
        let source = fs.readFileSync(temp_dir + "/" + language + "/" + f + fileExt, "utf-8");

        const newfile = valuesDirectory + f + '.xml';
        fs.writeFileSync(newfile, source);
        return replacechars(newfile, fileExt, isCrowdin);
    }
}

export async function updateI18nFiles() {
    for (let language of languages) {
        // Regional files need a marker in Android
        // https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources -
        // The language is defined by a two-letter ISO 639-1 language code, optionally followed by a two letter ISO 3166-1-alpha-2 region code (preceded by lowercase r).
        //
        // The codes are not case-sensitive; the r prefix is used to distinguish the region portion. You cannot specify a region alone.
        let androidLanguage = "";
        if (localizedRegions.includes(language.split("-", 1)[0])) {
            androidLanguage = language.replace('-', '-r') // zh-CW becomes zh-rCW
        } else {
            androidLanguage = language.split('-', 1)[0] // Example: es-ES becomes es
        }

        if (language == "yu") {
            androidLanguage = "yue";
        }
        if (language == "he") {
            androidLanguage = "heb";
        }
        if (language == "id") {
            androidLanguage = "ind";
        }
        if (language == "tl") {
            androidLanguage = "tgl";
        }

        console.log("\nCopying language files from " + language + " to " + androidLanguage);
        let valuesDirectory = path.join(__dirname, "../../../AnkiDroid/src/main/res/values-" + androidLanguage + "/");
        createIfNotExisting(valuesDirectory);
        
        // Copy localization files, mask chars and append gnu/gpl licence
        for (let f of fileNames) {
            let fileExt = fileExtFor(f);
            await update(valuesDirectory, f, fileExt, true, language).then(res => {
                anyError = res;
            });
        }

        if (!anyError) {
            console.log("At least one file of the last handled language contains an error.");
            anyError = true;
        }
    }
}
