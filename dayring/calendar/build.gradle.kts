plugins {
    alias(libs.plugins.android.application)
}

// Companion app: provides the watch face with the event rim (as an image) and the next event (as text).
android {
    namespace = "de.jmb0.dayring.calendar"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.jmb0.dayring.calendar"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
