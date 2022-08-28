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
export const checkI18nFile = (line: string) => {
    // TODO - implement VerifyJavaStringFormat to check broken strings
}

export const VerifyJavaStringFormat = (line: string) => {
    let argCount = 0;
    let nonpositional = false;

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

            argCount++;

            const numDigits = consumeDigits(line, index);

            if (numDigits > 0) {
                index += numDigits;

                if (index < line.length && c(index) != '$') {
                    // The digits were a size, but not a positional argument.
                    nonpositional = true;
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

    return true;
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
