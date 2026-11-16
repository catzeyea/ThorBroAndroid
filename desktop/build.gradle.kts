import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.core)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "dev.catzeyea.thorbro.desktop.MainKt"

        nativeDistributions {
            // Linux: .deb und AppImage-artig, Windows: .msi
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi)
            packageName = "ThorBro"
            packageVersion = "0.1.0"
        }
    }
}
