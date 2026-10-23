import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.9.3"
}

dependencies {
    implementation(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "io.github.motimotinyanko.otobako.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Otobako"
            packageVersion = "1.0.0"
            description = "動画サイトの曲と mp3 をタグ・アートワーク・音量まで整えて保存"
            vendor = "Otobako"
            modules("java.desktop", "java.prefs", "jdk.unsupported", "jdk.crypto.ec", "java.net.http")
            // ffmpeg / ffprobe / yt-dlp / deno (fetch-tools.ps1 が vendor\bin に用意 → build.ps1 が res\windows\bin へ)
            appResourcesRootDir.set(project.layout.projectDirectory.dir("res"))
            windows {
                iconFile.set(project.file("icon/otobako.ico"))
                menuGroup = "Otobako"
            }
        }
    }
}
