/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("io.element.android-compose-library")
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.libraries.designsystem"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    dependencies {
        api(libs.compound)
        // Should not be there, but this is a POC
        implementation(libs.coil.compose)
        implementation(libs.vanniktech.blurhash)
        implementation(projects.libraries.architecture)
        implementation(projects.libraries.testtags)
        implementation(projects.libraries.uiStrings)

        ksp(libs.showkase.processor)
        kspTest(libs.showkase.processor)

        testImplementation(libs.test.junit)
        testImplementation(libs.coroutines.test)
        testImplementation(libs.molecule.runtime)
        testImplementation(libs.test.truth)
        testImplementation(libs.test.turbine)
    }
}

// SC resource override
android {
    // Use a flavor for common things that the upstream config will not override by the build type
    flavorDimensions += "package"
    productFlavors {
        create("sc") {
            dimension = "package"
        }
    }
}
