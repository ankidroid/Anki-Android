#!/bin/bash
#
# Upload codacy coverage reports.
#
# Partial uploads for APIs, with an option to finalize.
#

set -e

 wget --progress=dot:giga -O ~/codacy-coverage-reporter-assembly-latest.jar https://oss.sonatype.org/service/local/repositories/releases/content/com/codacy/codacy-coverage-reporter/4.0.2/codacy-coverage-reporter-4.0.2-assembly.jar

if [[ ( "$API" != "NONE" ) ]]; then
    java -jar ~/codacy-coverage-reporter-assembly-latest.jar report \
        -l Java \
        -r AnkiDroid/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml \
        --partial
fi

if [[ ( "$FINALIZE_COVERAGE" == "TRUE" ) ]]; then
    java -jar ~/codacy-coverage-reporter-assembly-latest.jar final
fi

