import { VerifyJavaStringFormat } from "../src/check"

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

const pluralPass = `<resources>
    <plurals name="import_complete_message">
        <item quantity="one">Cards imported: %1\${'$'}d</item>
        <item quantity="other">Files imported :%1\${'$'}d" Total cards imported: %2\${'$'}d</item>
    </plurals>
</resources>`

const pluralPartial = `<resources>
    <plurals name="import_complete_message">
        <item quantity="one">Cards imported: %d</item>
        <item quantity="other">Files imported :%1\${'$'}d" Total cards imported: %2${'$'}d</item>
    </plurals>
</resources>`

const pluralFail = `<resources>
    <plurals name="import_complete_message">
        <item quantity="one">Cards imported: %d</item>
        <item quantity="other">Files imported: %d\nTotal cards imported: %d</item>
    </plurals>
</resources>`


const pluralMultiple = `<plurals name="reschedule_card_dialog_interval">
    <item quantity="one">Current interval: %d day</item>
    <item quantity="few">Keisti Kortelių mokymosi dieną</item>
    <item quantity="many">Current interval: %d days</item>
    <item quantity="other">Current interval: %d days</item>
</plurals>`

const pluralMultipleTwo = `<plurals name="in_minutes">
    <item quantity="one">%1\${'$'}d मिनट</item>
    <item quantity="other">मिनट</item>
</plurals>`

console.assert(VerifyJavaStringFormat(valid) === true, "[valid]", valid)
console.assert(VerifyJavaStringFormat(invalid) === false, "[invalid]", invalid)
console.assert(VerifyJavaStringFormat(unambiguous) === true, "[unambiguous]", unambiguous)
console.assert(VerifyJavaStringFormat(encoded) === true, "[encoded]", encoded)
console.assert(VerifyJavaStringFormat(pluralPass) === true, "[pluralPass]", pluralPass)
console.assert(VerifyJavaStringFormat(pluralPartial) === false, "[pluralPartial]", pluralPartial)
console.assert(VerifyJavaStringFormat(pluralFail) === false, "[pluralFail]", pluralFail)
console.assert(VerifyJavaStringFormat(pluralMultiple) === true, "[pluralMultiple]", pluralMultiple)
console.assert(VerifyJavaStringFormat(pluralMultipleTwo) === true, "[pluralMultipleTwo]", pluralMultipleTwo)
