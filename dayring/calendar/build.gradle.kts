import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Companion app: provides the watch face with the event rim (as an image) and the next event (as text).
android {
    namespace = "de.jmb0.dayring.calendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.jmb0.dayring.calendar"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing: reads keystore.properties in the project root (not in Git, see README).
    // Without it the release build is signed with the debug key and is only for trying it out;
    // Google Play rejects such a bundle.
    signingConfigs {
        val keystore = rootProject.file("keystore.properties")
        if (keystore.exists()) {
            val props = Properties().apply { keystore.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.wear.complications.data.source.ktx)
}
