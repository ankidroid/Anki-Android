/**
 * @author
 * AnkiDroid Open Source Team
 *
 * @license
 * Copyright (c) AnkiDroid. All rights reserved.
 * Licensed under the GPL-3.0 license. See LICENSE file in the project root for details.
 *
 * @description
 * checkI18nFile() will check broken string for individual line for xml files in AnkiDroid/src/main/res/values/ dir.
 */

/**
 * Check the line if it contains broken strings and return fixed strings
 * 
 * @param line string from xml file
 */

import fs from "fs";
import readline from "readline";
import { I18N_FILES, I18N_FILES_DIR } from "../src/constants";

export const checkI18nFile = () => {
    for (const file of I18N_FILES) {
        if (file == "15-markettitle") {
            continue;
        }

        if (file == "14-marketdescription") {
            continue;
        }

        let I18N_FILE_TARGET_NAME = `${file}.xml`;
        let I18N_FILE_SOURCE_NAME = `${I18N_FILES_DIR}${I18N_FILE_TARGET_NAME}`;

        checkStringFormatInFile(I18N_FILE_SOURCE_NAME);
    }
}

export const checkStringFormatInFile = async (filePath: string) => {
    try {
        const fileStream = fs.createReadStream(filePath);

        const rl = readline.createInterface({
            input: fileStream,
            crlfDelay: Infinity,
        });

        for await (let line of rl) {
            if (line.includes("%")) {
                if (!VerifyJavaStringFormat(line)) {
                    console.log(`Errors in file ${filePath} at line ${line}`);
                    return false;
                }
            }
        }

        return true;

    } catch (e) {
        console.log(e);
        return false;
    }
}

export const VerifyJavaStringFormat = (line: string) => {
    let argCount = 0;
    let nonpositional = false;
    let noerror = true;

    let index = 0;
    const c = (index: number) => {
        return line[index];
    }

    while (index < line.length) {
        if (c(index) === '%' && index + 1 < line.length) {
            index++;

            if (c(index) === '%' || c(index) === 'n') {
                index++;
                continue;
            }

            // ignore line if %.1f
            if (c(index) === '.') {
                index++;
                continue;
            }

            argCount++;

            const numDigits = consumeDigits(line, index);

            if (numDigits > 0) {
                index += numDigits;

                if (index < line.length && c(index) != '$') {
                    // The digits were a size, but not a positional argument.
                    nonpositional = true;
                }

                // catch %1$ d, space between positional
                if (index < line.length && c(index) === '$' && (c(index + 1) === ' ')) {
                    noerror = false;
                }

            } else if (c(index) === '<') {
                // Reusing last argument, bad idea since positions can be moved around
                // during translation.
                nonpositional = true;

                index++;

                // Optionally we can have a $ after
                if (index <= line.length && c(index) === '$') {
                    index++;
                }
            } else {
                nonpositional = true;
            }

            // Ignore size, width, flags, etc.
            while (index < line.length && (
                c(index) === '-' || c(index) === '#' || c(index) === '+' ||
                c(index) === ' ' || c(index) === ',' || c(index) === '(' ||
                isDigit(c(index))
            )) {
                index++;
            }

            /*
            * This is a shortcut to detect strings that are going to Time.format()
            * instead of String.format()
            *
            * Comparison of String.format() and Time.format() args:
            *
            * String: ABC E GH  ST X abcdefgh  nost x
            *   Time:    DEFGHKMS W Za  d   hkm  s w yz
            *
            * Therefore we know it's definitely Time if we have:
            *     DFKMWZkmwyz
            */
            if (index < line.length) {
                switch (c(index)) {
                    case 'D':
                    case 'F':
                    case 'K':
                    case 'M':
                    case 'W':
                    case 'Z':
                    case 'k':
                    case 'm':
                    case 'w':
                    case 'y':
                    case 'z':
                        return true;
                }
            }
        }

        if (index < line.length) {
            index++;
        }
    }

    if (argCount > 1 && nonpositional) {
        // Multiple arguments were specified, but some or all were non positional.
        // Translated
        // strings may rearrange the order of the arguments, which will break the
        // string.
        return false;
    }

    return noerror;
}

const consumeDigits = (s: string, index: number) => {
    let digits = 0;

    for (let i = index; i < s.length; i++) {
        if (!(isDigit(s[i]))) {
            return digits;
        }
        digits++;
    }

    return 0;
}

const isDigit = (char: string) => {
    return char.length === 1 && char >= '0' && char <= '9';
}
