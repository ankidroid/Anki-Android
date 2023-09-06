plugins {
    id 'kotlin'
}

tasks.withType(JavaCompile).configureEach {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).all {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11 // starting with AGP 7.4.0 we need to target JVM 11 bytecode
    }
}

dependencies {
    compileOnly "com.android.tools.lint:lint-api:$lint_version"
    compileOnly "com.android.tools.lint:lint:$lint_version"

    testImplementation "org.hamcrest:hamcrest:$hamcrest_version"
    testImplementation "org.hamcrest:hamcrest-library:$hamcrest_version"
    testImplementation "org.junit.jupiter:junit-jupiter:$junit_version"
    testImplementation "org.junit.vintage:junit-vintage-engine:$junit_version"
    testImplementation "com.android.tools.lint:lint:$lint_version"
    testImplementation "com.android.tools.lint:lint-api:$lint_version"
    testImplementation "com.android.tools.lint:lint-tests:$lint_version"
}
