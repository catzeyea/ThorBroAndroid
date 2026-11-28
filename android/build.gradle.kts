import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

/**
 * The release key, from environment variables or the untracked file `signing.properties` in the
 * repository root (see README, "Signing"). Nothing secret is in git. Without a key, the release APK
 * stays unsigned, so a build with the wrong key can never be published by mistake.
 */
data class ReleaseKey(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun releaseKey(): ReleaseKey? {
    val file = rootProject.file("signing.properties")
    val props = Properties().apply { if (file.isFile) file.inputStream().use { load(it) } }
    fun value(env: String, key: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: props.getProperty(key)?.takeIf { it.isNotBlank() }
    val store = value("THORBRO_KEYSTORE", "storeFile") ?: return null
    return ReleaseKey(
        storeFile = File(store),
        storePassword = value("THORBRO_KEYSTORE_PASSWORD", "storePassword") ?: return null,
        keyAlias = value("THORBRO_KEY_ALIAS", "keyAlias") ?: return null,
        keyPassword = value("THORBRO_KEY_PASSWORD", "keyPassword") ?: return null,
    )
}

val releaseKey = releaseKey()
if (releaseKey == null) {
    logger.warn("ThorBro: no release key (signing.properties or THORBRO_KEYSTORE…), the release APK will be UNSIGNED.")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    // namespace = Kotlin package where the generated R class lives.
    // applicationId = the app's unique ID on the device (used by adb and `am start -n`).
    namespace = "dev.catzeyea.thorbro.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.catzeyea.thorbro"
        // The Thor runs Android 13 = API 33. Set here only, because the shared catalog value
        // (android-minSdk = 30) is still used by the core module.
        minSdk = 33
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.2.0"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey.storeFile
                storePassword = releaseKey.storePassword
                keyAlias = releaseKey.keyAlias
                keyPassword = releaseKey.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // The dedicated release key (since 0.2.0). Android only installs an update signed with the
            // same key as the installed app, so this key must never change or get lost.
            signingConfig = if (releaseKey != null) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // The core module is intentionally not used by the stream launcher (v1).
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.serialization.json)

    // Local unit tests (run on the PC's JVM, no device needed): ./gradlew :android:testDebugUnitTest
    testImplementation(libs.junit)
}
