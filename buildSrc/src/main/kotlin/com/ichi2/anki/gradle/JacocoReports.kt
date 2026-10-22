// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.file.ProjectLayout
import org.gradle.kotlin.dsl.assign
import org.gradle.testing.jacoco.tasks.JacocoReport

/** Configures XML, HTML, and optional browser opening for the merged report. */
fun JacocoReport.configureMergedReportOutput(
    openReportRequested: Boolean,
    linuxHtmlCmd: String?,
) {
    val htmlOutDir =
        project.layout.buildDirectory
            .dir("reports/jacoco/$name/html")
            .get()
            .asFile
    val openReport = openReportRequested
    val htmlCmd = linuxHtmlCmd

    doLast {
        ReportOpener.open(htmlOutDir, openReport, htmlCmd)
    }

    reports {
        xml.required = true
        html.outputLocation = htmlOutDir
    }
}

/** Configures XML, HTML, and optional browser opening for the unit-test report. */
fun JacocoReport.configureUnitReportOutput(
    layout: ProjectLayout,
    openReportRequested: Boolean,
    linuxHtmlCmd: String?,
) {
    val htmlOutDir =
        layout.buildDirectory
            .dir("reports/jacoco/$name/html")
            .get()
            .asFile
    val openReport = openReportRequested
    val htmlCmd = linuxHtmlCmd

    doLast {
        ReportOpener.open(htmlOutDir, openReport, htmlCmd)
    }

    reports {
        xml.required = true
        html.outputLocation = htmlOutDir
    }
}

/** Configures XML, HTML, and optional browser opening for the Android-test report. */
fun JacocoReport.configureAndroidReportOutput(
    layout: ProjectLayout,
    openReportRequested: Boolean,
    linuxHtmlCmd: String?,
) {
    val htmlOutDir =
        layout.buildDirectory
            .dir("reports/jacoco/$name/html")
            .get()
            .asFile
    val openReport = openReportRequested
    val htmlCmd = linuxHtmlCmd

    doLast {
        ReportOpener.open(htmlOutDir, openReport, htmlCmd)
    }

    reports {
        xml.required = true
        html.outputLocation = htmlOutDir
    }
}
