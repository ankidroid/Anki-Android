/*
 *  Copyright (c) 2024 Abd-Elrahman Esam <abdelrahmanesam20000@gmail.com>
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
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File


//todo when we know how ..... share the same plugin between application and library
// it should happen with commonExtension class but for some reason it doesn't work
class LintLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureLintRules()
        }
    }
}

private fun Project.configureLintRules() {
    extensions.configure<LibraryExtension> {
        lint {
            abortOnError = true
            checkReleaseBuilds = true
            checkTestSources = true
            explainIssues = false
            lintConfig = File("../lint-release.xml")
            showAll = true
            // To output the lint report to stdout set textReport=true, and leave textOutput unset.
            textReport = true
            warningsAsErrors = true

            if (System.getenv("CI") == "true") {
                // 14853: we want this to appear in the IDE, but it adds noise to CI
                disable += "WrongThread"
            }
        }
    }
}