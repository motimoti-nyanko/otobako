# 同梱ソフトウェア

Otobako は以下のソフトウェアを同梱・利用しています。各ソフトウェアの著作権はそれぞれの作者に帰属します。

## Android 版・Windows 版共通

| ソフトウェア | ライセンス | 入手先 |
| --- | --- | --- |
| yt-dlp | The Unlicense | https://github.com/yt-dlp/yt-dlp |
| FFmpeg | GPL v3 (GPL 版ビルド) | https://ffmpeg.org/ |
| Jetpack Compose / Compose Multiplatform | Apache License 2.0 | https://github.com/JetBrains/compose-multiplatform |
| Kotlin / kotlinx.coroutines | Apache License 2.0 | https://kotlinlang.org/ |

## Android 版

| ソフトウェア | ライセンス | 入手先 |
| --- | --- | --- |
| youtubedl-android (yt-dlp・Python・FFmpeg・QuickJS を含む) | GPL v3 | https://github.com/JunkFood02/youtubedl-android |
| AndroidX (Media3, Palette, DocumentFile ほか) | Apache License 2.0 | https://developer.android.com/jetpack/androidx |

## Windows 版

| ソフトウェア | ライセンス | 入手先 |
| --- | --- | --- |
| FFmpeg 8.1 (BtbN ビルド, win64-gpl-shared) | GPL v3 | ビルド: https://github.com/BtbN/FFmpeg-Builds ・ ソース: https://ffmpeg.org/download.html |
| Deno (yt-dlp が YouTube の処理に使う JavaScript 実行環境) | MIT License | https://github.com/denoland/deno |
| Eclipse Temurin (Java ランタイム) | GPL v2 + Classpath Exception | https://adoptium.net/ |
| Skia / Skiko | BSD 3-Clause / Apache License 2.0 | https://github.com/JetBrains/skiko |
| JSON-java (org.json) | Public Domain | https://github.com/stleary/JSON-java |

FFmpeg・youtubedl-android は GPL のため、Otobako も GPL v3 で公開しています。
同梱した FFmpeg のソースコードは上記の入手先から取得できます。
