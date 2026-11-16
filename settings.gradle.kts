rootProject.name = "ThorBro"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// core    = gemeinsame Logik (Modelle, Konsolenerkennung, Library-Suche, Save-Sync)
// desktop = PC-App für Windows und Linux (Compose Desktop, spricht adb)
// android = APK für den Thor (Compose, lokaler Dateizugriff)
include(":core", ":desktop", ":android")
