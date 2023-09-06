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
    compileOnly("com.android.tools.lint:lint-api:${rootProject.extra["lint_version"]}")
    compileOnly("com.android.tools.lint:lint:${rootProject.extra["lint_version"]}")

    testImplementation("org.hamcrest:hamcrest:${rootProject.extra["hamcrest_version"]}")
    testImplementation("org.hamcrest:hamcrest-library:${rootProject.extra["hamcrest_version"]}")
    testImplementation("org.junit.jupiter:junit-jupiter:${rootProject.extra["junit_version"]}")
    testImplementation("org.junit.vintage:junit-vintage-engine:${rootProject.extra["junit_version"]}")
    testImplementation("com.android.tools.lint:lint:${rootProject.extra["lint_version"]}")
    testImplementation("com.android.tools.lint:lint-api:${rootProject.extra["lint_version"]}")
    testImplementation("com.android.tools.lint:lint-tests:${rootProject.extra["lint_version"]}")
}
