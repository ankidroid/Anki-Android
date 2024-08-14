import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("kotlin")
}

tasks.withType(JavaCompile::class).configureEach {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}

tasks.withType(KotlinCompile::class).all {
    compilerOptions {
        // starting with AGP 7.4.0 we need to target JVM 11 bytecode
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    compileOnly(libs.android.lint.api)
    compileOnly(libs.android.lint)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.android.lint.api)
    testImplementation(libs.android.lint)
    testImplementation(libs.android.lint.tests)
}
