/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Not published on its own: the app in `calendar` carries this APK and installs it with
// Watch Face Push, which requires the package name <app package>.watchfacepush.<name>.
android {
    enableKotlin = false
    namespace = "de.jmb0.dayring.calendar.watchfacepush.dayring"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.jmb0.dayring.calendar.watchfacepush.dayring"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing: reads keystore.properties in the project root (not in Git, see README).
    // Google recommends a key of its own for watch faces installed by Watch Face Push.
    // Without the file the release build is signed with the debug key, only for trying it out.
    signingConfigs {
        val keystore = rootProject.file("keystore.properties")
        if (keystore.exists()) {
            val props = Properties().apply { keystore.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("watchfaceKeyAlias")
                keyPassword = props.getProperty("watchfaceKeyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Strips the empty dex file; Watch Face Push rejects APKs with anything but resources.
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
            // Ensure shrink resources is false, to avoid potential for them
            // being removed.
            isShrinkResources = false

            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}
