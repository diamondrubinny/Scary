// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

pluginManagement {
    repositories {
        // Pass -Pscary.coreOnly to build only the pure-Kotlin :core module (no Android SDK or Google Maven needed).
        if (!providers.gradleProperty("scary.coreOnly").isPresent) {
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!providers.gradleProperty("scary.coreOnly").isPresent) {
            google()
        }
        mavenCentral()
    }
}

rootProject.name = "Scary"

include(":core")
if (!providers.gradleProperty("scary.coreOnly").isPresent) {
    include(":app")
}
