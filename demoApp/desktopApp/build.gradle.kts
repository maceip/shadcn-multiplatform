import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":demoApp:commonApp"))
    // Bundle the matching engine: the demo works offline and never downloads a browser at runtime.
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val architecture = when (arch) {
        "amd64", "x86_64" -> "amd64"
        "aarch64", "arm64" -> "arm64"
        else -> error("No bundled JCEF engine is configured for architecture $arch")
    }
    val platform = when {
        os.startsWith("windows") -> "windows-$architecture"
        os.contains("mac") -> "macosx-$architecture"
        os.contains("linux") -> "linux-$architecture"
        else -> error("No bundled JCEF engine is configured for operating system $os")
    }
    runtimeOnly("me.friwi:jcef-natives-$platform:${libs.versions.jcefNatives.get()}")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs += "--add-opens=java.desktop/sun.awt=ALL-UNNAMED"
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            jvmArgs += listOf("--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        nativeDistributions {
            modules("java.desktop", "java.management", "jdk.unsupported", "jdk.security.auth")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "shadcn-multiplatform"
            packageVersion = "1.0.0"

            linux {
                iconFile.set(project.file("appIcons/LinuxIcon.png"))
            }
            windows {
                iconFile.set(project.file("appIcons/WindowsIcon.ico"))
            }
            macOS {
                iconFile.set(project.file("appIcons/MacosIcon.icns"))
                bundleID = "com.github.jershell.shadcn.desktopApp"
            }
        }
    }
}
