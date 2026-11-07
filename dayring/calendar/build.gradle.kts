import com.google.android.wearable.watchface.validator.client.DwfValidatorFactory
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The app that is published: provides the watch face with the event rim (as an image) and the next
// event (as text), and installs the watch face from `:watchface` with Watch Face Push.
android {
    namespace = "de.jmb0.dayring.calendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.jmb0.dayring.calendar"
        // Watch Face Push needs Wear OS 6
        minSdk = 36
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
    implementation(libs.wear.watchfacepush)
    implementation(libs.kotlinx.coroutines.android)
}

/**
 * Validates the watch face APK for Watch Face Push and bundles it: the APK goes into the assets as
 * `default_watchface.apk`, the validation token into the string resource `default_wf_token`. With
 * both, Wear OS installs the watch face together with this app.
 */
abstract class BundleWatchFaceTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val watchFaceApk: RegularFileProperty

    @get:Input
    abstract val appPackage: Property<String>

    @get:OutputDirectory
    abstract val assetsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val apk = watchFaceApk.get().asFile
        val result = DwfValidatorFactory.create().validate(apk, appPackage.get())
        val failures = result.failures()
        if (failures.isNotEmpty()) {
            throw GradleException(
                "The watch face fails the Watch Face Push validation:\n" +
                    failures.joinToString("\n") { "- $it" }
            )
        }
        val token = result.validationToken()
        if (token.isEmpty()) throw GradleException("The validator returned no token for $apk")

        apk.copyTo(assetsDir.get().file("default_watchface.apk").asFile, overwrite = true)
        // Quoted, so that characters such as ' or a leading @ in the token stay as they are
        val escaped = token.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("&", "&amp;").replace("<", "&lt;")
        resDir.get().file("values/default_watchface_token.xml").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |<?xml version="1.0" encoding="utf-8"?>
                |<resources>
                |    <string name="default_wf_token" translatable="false">"$escaped"</string>
                |</resources>
                |""".trimMargin()
            )
        }
    }
}

// Every variant of the app carries the release build of the watch face; without keystore.properties
// that is signed with the debug key, which is enough for trying it out.
androidComponents {
    onVariants { variant ->
        val task = tasks.register<BundleWatchFaceTask>(
            "bundleWatchFace" + variant.name.replaceFirstChar(Char::titlecase)
        ) {
            dependsOn(":watchface:assembleRelease")
            watchFaceApk.set(
                project(":watchface").layout.buildDirectory
                    .file("outputs/apk/release/watchface-release.apk")
            )
            appPackage.set(variant.applicationId)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, BundleWatchFaceTask::assetsDir)
        variant.sources.res?.addGeneratedSourceDirectory(task, BundleWatchFaceTask::resDir)
    }
}
