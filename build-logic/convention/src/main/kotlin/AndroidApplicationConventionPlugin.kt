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
import com.ichi2.anki.androidTestImplementation
import com.ichi2.anki.annotationProcessor
import com.ichi2.anki.api
import com.ichi2.anki.compileOnly
import com.ichi2.anki.configureCommonFeaturesForApplicationPlugin
import com.ichi2.anki.coreLibraryDesugaring
import com.ichi2.anki.debugImplementation
import com.ichi2.anki.implementation
import com.ichi2.anki.libs
import com.ichi2.anki.lintChecks
import com.ichi2.anki.tasks.assertNonZeroAndroidTests
import com.ichi2.anki.tasks.preBuildTask
import com.ichi2.anki.testImplementation
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {

            extensions.configure<ApplicationExtension> {
                namespace = "com.ichi2.anki"
                configureCommonFeaturesForApplicationPlugin(this)
                dependencies {
                    configurations.configureEach {
                        resolutionStrategy {
                            // Timber has this as a dependency but they are not up to date. We want to force our version.
                            force("org.jetbrains:annotations:24.1.0")
                        }
                    }
                    api(project(":api"))
                    implementation(libs.androidx.work.runtime)
                    lintChecks(project(":lint-rules"))
                    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

                    compileOnly(libs.jetbrains.annotations)
                    compileOnly(libs.auto.service.annotations)
                    annotationProcessor(libs.auto.service)
                    implementation(libs.androidx.activity.ktx)
                    implementation(libs.androidx.annotation)
                    implementation(libs.androidx.appcompat)
                    implementation(libs.androidx.browser)
                    implementation(libs.androidx.core.ktx)
                    implementation(libs.androidx.exifinterface)
                    implementation(libs.androidx.fragment.ktx)
                    implementation(libs.androidx.media)
                    implementation(libs.androidx.preference.ktx)
                    implementation(libs.androidx.recyclerview)
                    implementation(libs.androidx.sqlite.framework)
                    implementation(libs.androidx.swiperefreshlayout)
                    implementation(libs.androidx.viewpager2)
                    implementation(libs.androidx.constraintlayout)
                    implementation(libs.androidx.webkit)
                    implementation(libs.google.material)
                    implementation(libs.android.image.cropper)
                    implementation(libs.nanohttpd)
                    implementation(libs.kotlinx.serialization.json)
                    implementation(libs.seismic)

                    debugImplementation(libs.androidx.fragment.testing.manifest)

                    // Backend libraries

                    implementation(libs.protobuf.kotlin.lite) // This is required when loading from a file)

                    val localProperties = Properties()
                    if (project.rootProject.file("local.properties").exists()) {
                        localProperties.load(
                            project.rootProject.file("local.properties").inputStream()
                        )
                    }
                    if (localProperties.getProperty("local_backend") == "true") {
                        implementation(files("../../Anki-Android-Backend/rsdroid/build/outputs/aar/rsdroid-release.aar"))
                        testImplementation(files("../../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar"))
                    } else {
                        implementation(libs.ankiBackend.backend)
                        implementation(libs.ankiBackend.testing)
                    }

                    // May need a resolution strategy for support libs to our versions
                    implementation(libs.acra.limiter)
                    implementation(libs.acra.toast)
                    implementation(libs.acra.dialog)
                    implementation(libs.acra.http)

                    implementation(libs.materialDialogs.core)
                    implementation(libs.materialDialogs.input)
                    implementation(libs.commons.compress)
                    implementation(libs.commons.collections4) // SetUniqueList)
                    implementation(libs.commons.io) // FileUtils.contentEquals)
                    implementation(libs.mikehardy.google.analytics.java7)
                    implementation(libs.okhttp)
                    implementation(libs.slf4j.timber)
                    implementation(libs.jakewharton.timber)
                    implementation(libs.jsoup)
                    implementation(libs.java.semver) // For AnkiDroid JS API Versioning)
                    implementation(libs.drakeet.drawer)
                    implementation(libs.tapTargetPrompt)
                    implementation(libs.colorpicker)
                    implementation(libs.kotlin.reflect)
                    implementation(libs.kotlin.test)
                    implementation(libs.search.preference)
                    implementation(libs.androidx.work.testing)

                    // Cannot use debugImplementation since classes need to be imported in AnkiDroidApp
                    // and there's no no-op version for release build. Usage has been disabled for release
                    // build via AnkiDroidApp.
                    implementation(libs.leakcanary.android)

                    testImplementation(project(":testlib"))

                    // A path for a testing library which provide Parameterized Test
                    testImplementation(libs.junit.jupiter)
                    testImplementation(libs.junit.jupiter.params)
                    testImplementation(libs.junit.vintage.engine)
                    testImplementation(libs.mockito.inline)
                    testImplementation(libs.mockito.kotlin)
                    testImplementation(libs.hamcrest)
                    testImplementation(libs.androidx.work.testing)
                    // robolectricDownloader.gradle *may* need a new SDK jar entry if they release one or if we change targetSdk. Instructions in that gradle file.
                    testImplementation(libs.robolectric)
                    testImplementation(libs.androidx.test.core)
                    testImplementation(libs.androidx.test.junit)
                    testImplementation(libs.kotlin.reflect)
                    testImplementation(libs.kotlin.test)
                    testImplementation(libs.kotlin.test.junit5)
                    testImplementation(libs.kotlinx.coroutines.test)
                    testImplementation(libs.mockk)
                    testImplementation(libs.commons.exec) // obtaining the OS)
                    testImplementation(libs.androidx.fragment.testing)
                    // in a JvmTest we need org.json.JSONObject to not be mocked
                    testImplementation(libs.json)
                    testImplementation(libs.ivanshafran.shared.preferences.mock)
                    testImplementation(libs.androidx.test.runner)
                    testImplementation(libs.androidx.test.rules)
                    testImplementation(libs.androidx.espresso.core)
                    testImplementation(libs.androidx.espresso.contrib)

                    androidTestImplementation(project(":testlib"))

                    // May need a resolution strategy for support libs to our versions
                    androidTestImplementation(libs.androidx.espresso.core)
                    androidTestImplementation(libs.androidx.espresso.contrib)
                    androidTestImplementation(libs.androidx.test.core)
                    androidTestImplementation(libs.androidx.test.junit)
                    androidTestImplementation(libs.androidx.test.rules)
                    androidTestImplementation(libs.androidx.uiautomator)
                    androidTestImplementation(libs.kotlin.test)
                    androidTestImplementation(libs.kotlin.test.junit)
                    androidTestImplementation(libs.androidx.fragment.testing)
                }
            }

            preBuildTask()
            assertNonZeroAndroidTests()

            //todo migrate task connectedPlayDebugAndroidTest when we know how to solve it's issue
        }
    }


}
