import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
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
        versionCode = 2
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.kotlinx.serialization.json)

    // Local unit tests (run on the PC's JVM, no device needed): ./gradlew :android:testDebugUnitTest
    testImplementation(libs.junit)
}
