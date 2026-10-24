// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Exposed because CinemetaCache is serializable and the app stores it.
    api(libs.kotlinx.serialization.json)
    // QR codes (the donation code in settings). Pure Java, Apache-2.0; only its encoder ships, the tests also decode.
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}

// Tests read files outside this module (the bundled catalog, NOTICE and LICENSE) and core's own sources
// (AndroidCompatibilityTest). Declared as inputs, so changing them reruns the tests instead of reusing old results.
tasks.test {
    inputs.file("../app/src/main/assets/catalog.json")
    inputs.files("../NOTICE", "../LICENSE")
    inputs.dir("src/main/kotlin")
}
