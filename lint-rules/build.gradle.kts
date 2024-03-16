import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("kotlin")
}

tasks.withType(JavaCompile::class).configureEach {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        // starting with AGP 7.4.0 we need to target JVM 11 bytecode
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    compileOnly(libs.com.android.tools.lint.api)
    compileOnly(libs.com.android.tools.lint)
    testImplementation(libs.org.hamcrest.hamcrest)
    testImplementation(libs.org.hamcrest.hamcrest.library)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.com.android.tools.lint.api)
    testImplementation(libs.com.android.tools.lint)
    testImplementation(libs.com.android.tools.lint.tests)
}
