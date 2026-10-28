import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.motimotinyanko.otobako"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.motimotinyanko.otobako"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        // yt-dlp (Python) と ffmpeg の実行ファイルが ABI ごとに 30MB 以上あるので 64bit ARM だけ同梱
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // リリース署名の鍵はリポジトリに入れない。ホームの .otobako-release/keystore.properties
    // (または環境変数 OTOBAKO_KEYSTORE_PROPERTIES が指すファイル) があるときだけ使う
    val keystoreProps = (System.getenv("OTOBAKO_KEYSTORE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.otobako-release/keystore.properties").let(::file)
    signingConfigs {
        if (keystoreProps.exists()) create("release") {
            val p = Properties().apply { keystoreProps.inputStream().use(::load) }
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // libffmpeg.so / libpython.so を実行ファイルとして使うため nativeLibraryDir へ展開させる
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // yt-dlp + ffmpeg (Python ごと同梱)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    testImplementation("junit:junit:4.13.2")
}
