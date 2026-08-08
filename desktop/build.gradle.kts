import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    kotlin("jvm")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.core)
    
    val osName = System.getProperty("os.name").lowercase()
    val javafxPlatform = when {
        osName.contains("win") -> "win"
        osName.contains("mac") -> "mac"
        else -> "linux"
    }
    implementation("org.openjfx:javafx-base:17.0.2:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:17.0.2:$javafxPlatform")
    implementation("org.openjfx:javafx-media:17.0.2:$javafxPlatform")

compose.desktop {
    application {
        mainClass = "com.kuhoo.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Kuhoo"
            packageVersion = "1.0.0"
            description = "Kuhoo Music Player - Cross Platform Music Player"
            copyright = "© 2026 Kuhoo. All rights reserved."

            windows {
                menuGroup = "Kuhoo Music"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                iconFile.set(project.file("src/main/resources/kuhoo_icon.png"))
            }
        }
    }
}
}