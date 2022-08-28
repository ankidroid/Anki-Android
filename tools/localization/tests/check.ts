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

const invalidStr = `<resources>
    <string name="testString">I am a test% String</string>
    <string name="testString2">test%</string>
    <string name="testString3">test% string</string>
    <plurals name="pluralTestString1">
        <item quantity="other">आज%  %1\${'$'}'d' मध्ये% %2\${'$'}'s' कार्डांचा अभ्यास केला</item>
    </plurals>
</resources>
`;

const validStr = `<resources>
    <string name="testString">I am a test String</string>
    <string name="testString2">test</string>
    <string name="testString3">test string</string>
    <string name="testString4">test string %s</string>
    <string name="testString5">%%</string>
    <string name="testString6">%1\$'d' is expected</string>
    <plurals name="PluralTestString1">
        <item quantity="one">%1$'d' card (0 due)</item>
        <item quantity="other">%1$'d' cards (0 due)</item>
    </plurals>
    <plurals name="pluralTestString2">
        <item quantity="one">आज %1\${'$'}'d' मध्ये %2\${'$'}'s' कार्डचा अभ्यास केला</item>
    </plurals>
    <string name="testString7">XXX%</string>
</resources>
`;

console.assert(VerifyJavaStringFormat(valid) === true, "[valid]", valid)
console.assert(VerifyJavaStringFormat(invalid) === false, "[invalid]", invalid)
console.assert(VerifyJavaStringFormat(unambiguous) === true, "[unambiguous]", unambiguous)
console.assert(VerifyJavaStringFormat(encoded) === true, "[encoded]", encoded)
console.assert(VerifyJavaStringFormat(pluralPass) === true, "[pluralPass]", pluralPass)
console.assert(VerifyJavaStringFormat(pluralPartial) === false, "[pluralPartial]", pluralPartial)
console.assert(VerifyJavaStringFormat(pluralFail) === false, "[pluralFail]", pluralFail)
console.assert(VerifyJavaStringFormat(pluralMultiple) === true, "[pluralMultiple]", pluralMultiple)
console.assert(VerifyJavaStringFormat(pluralMultipleTwo) === true, "[pluralMultipleTwo]", pluralMultipleTwo)
console.assert(VerifyJavaStringFormat(invalidStr) === false, "[invalidStr]", invalidStr)
console.assert(VerifyJavaStringFormat(validStr) === false, "[validStr]", invalidStr)
