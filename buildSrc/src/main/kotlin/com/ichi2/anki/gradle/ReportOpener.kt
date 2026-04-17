/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.gradle

import org.gradle.internal.os.OperatingSystem
import java.io.File

/**
 * Prints the location of a generated HTML report
 *
 * @see open
 */
object ReportOpener {
    /**
     * Prints the location of a generated HTML report and, if [openRequested] is true,
     * tries to open it in the system default browser.
     */
    @JvmStatic
    fun open(
        htmlOutDir: File,
        openRequested: Boolean,
        linuxHtmlCmd: String?,
    ) {
        val reportPath = "$htmlOutDir/index.html"
        println("HTML Report: file://$reportPath")

        if (!openRequested) {
            println("to open the report automatically in your default browser add '-Popen-report' cli argument")
            return
        }

        val os = OperatingSystem.current()
        when {
            os.isWindows -> ProcessBuilder("cmd", "/c", "start $reportPath").start()
            os.isMacOsX -> ProcessBuilder("open", reportPath).start()
            os.isLinux ->
                try {
                    ProcessBuilder("xdg-open", reportPath).start()
                } catch (ignored: Exception) {
                    if (!linuxHtmlCmd.isNullOrEmpty()) {
                        ProcessBuilder(linuxHtmlCmd, reportPath).start()
                    } else {
                        println("'linux-html-cmd' property could not be found in 'local.properties'")
                    }
                }
        }
    }
}
