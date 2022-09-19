import fs from "fs";
import { checkStringFormatInFile } from "../src/check";
import { TEMP_DIR } from "../src/constants";

/* One substitution is unambiguous */
const valid = `<resources>
    <string name="hello">%s</string>
</resources>`

/** Easiest test case: Two exact duplicates in the same file  */
const invalid = `<resources>
    <string name="hello">%s %s</string>
</resources>`

const unambiguous = `<resources><string name=\"hello\">%1\$s %2\$s</string></resources>`

/** %% is an encoded percentage */
const encoded = "<resources><string name=\"hello\">%%</string></resources>"

/** should not contain space(s) after $ */
const validFormat = `<resources><string name="field_remapping">%1$s (from “%2$s”)</string></resources>`
// space in %1$ s
const inValidFormat = `<resources><string name="field_remapping">%1$ s (from “%2$s”)</string></resources>`

const validFloat = `<string name="stats_overview_average_answer_time">Average answer time: &lt;b&gt;%1$.1fs&lt;/b&gt; (&lt;b&gt;%2$.2f&lt;/b&gt; cards/minute)</string>`;

const createTempFile = (name: string, str: string) => {
    fs.writeFile(`${TEMP_DIR}/${name}`, str, function (err) {
        if (err) {
            return console.log(err);
        }
    });
}

describe("test valid", () => {
    let name = "valid";
    createTempFile(name, valid);

    it("should return true", async () => {
        expect(await checkStringFormatInFile(`${TEMP_DIR}/${name}`)).toBe(true);
    });
});

describe("test invalid", () => {
    let name = "invalid";
    createTempFile(name, invalid);

    it("should return false", async () => {
        expect(await checkStringFormatInFile(`${TEMP_DIR}/${name}`)).toBe(false);
    });
});

describe("test unambiguous", () => {
    let name = "unambiguous";
    createTempFile(name, unambiguous);

    it("should return true", async () => {
        expect(await checkStringFormatInFile(`${TEMP_DIR}/${name}`)).toBe(true);
    });
});

describe("test encoded", () => {
    let name = "encoded";
    createTempFile(name, encoded);

    it("should return true", async () => {
        expect(await checkStringFormatInFile(`${TEMP_DIR}/${name}`)).toBe(true);
    });
});

describe("test validFormat", () => {
    let name = "validFormat";
    createTempFile(name, validFormat);

    it("should return true", async () => {
        expect(await checkStringFormatInFile(`${TEMP_DIR}/${name}`)).toBe(true);
    });
});

describe("test inValidFormat", () => {
    let name = "inValidFormat";
    createTempFile(name, inValidFormat);

    it("should return false", async () => {
        expect(await checkStringFormatInFile(`${TEMP_DIR}/${name}`)).toBe(false);
    });
});

describe("test validFloat", () => {
    let name = "validFloat";
    createTempFile(name, validFloat);

    it("should return true", async () => {
        expect(await checkStringFormatInFile(`${TEMP_DIR}/${name}`)).toBe(true);
    });
});
