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
import com.android.build.api.dsl.ApplicationExtension
import com.ichi2.anki.annotationProcessor
import com.ichi2.anki.api
import com.ichi2.anki.compileOnly
import com.ichi2.anki.configureCommonFeaturesForApplicationPlugin
import com.ichi2.anki.coreLibraryDesugaring
import com.ichi2.anki.debugImplementation
import com.ichi2.anki.dependencies.androidTestImplementationDependencies
import com.ichi2.anki.dependencies.implementationDependencies
import com.ichi2.anki.dependencies.testImplementationDependencies
import com.ichi2.anki.libs
import com.ichi2.anki.lintChecks
import com.ichi2.anki.tasks.assertNonZeroAndroidTests
import com.ichi2.anki.tasks.connectedPlayDebugAndroidTest
import com.ichi2.anki.tasks.idea
import com.ichi2.anki.tasks.preBuildTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {

            extensions.configure<ApplicationExtension> {
                namespace = "com.ichi2.anki"
                configureCommonFeaturesForApplicationPlugin(this)
                idea()
            }

            preBuildTask()
            assertNonZeroAndroidTests()
            connectedPlayDebugAndroidTest()

            dependencies {
                configurations.configureEach {
                    resolutionStrategy {
                        // Timber has this as a dependency but they are not up to date. We want to force our version.
                        force("org.jetbrains:annotations:24.1.0")
                    }
                }



                implementationDependencies(target)
                testImplementationDependencies(target)
                androidTestImplementationDependencies(target)
                api(project(":api"))
                lintChecks(project(":lint-rules"))
                coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
                compileOnly(libs.jetbrains.annotations)
                compileOnly(libs.auto.service.annotations)
                annotationProcessor(libs.auto.service)
                debugImplementation(libs.androidx.fragment.testing.manifest)

            }

        }
    }


}
