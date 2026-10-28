# Otobako

動画サイトの曲や手元の mp3 を、タグ・アートワーク・長さ・音量まで整えて保存するアプリです。
Android 版と Windows 版があります。

- YouTube / ニコニコ動画 / SoundCloud / X / TikTok のリンクから音声を取り込み、アプリ内に保留して一覧表示
- サムネイル (最大 1080p) を取得し、正方形に切り抜いてアートワークに
- 曲名・アーティスト・アルバム・作曲者などのタグ編集 (タイトルからの推測・全曲への一括適用つき)
- 波形を見ながら前後のカット、フェードイン/アウト
- mp3gain と同じ基準 (89 dB) で音量をそろえて一括保存
- 端末 / PC 内の mp3 も編集可能。再エンコードせず無劣化で上書き (音量は 1.5 dB 刻み)
- 端末 / PC 内の mp4 などの動画から音声を取り出して mp3 に変換 (元の動画はそのまま。1 コマをアートワークに)

## ダウンロード

[Releases](../../releases) から入手してください。

| 版 | ファイル | 動作環境 |
| --- | --- | --- |
| Android | `Otobako-<版>.apk` | Android 10 以上 (64bit ARM) |
| Windows | `Otobako-<版>-windows-x64.zip` | Windows 10 / 11 (64bit) |

各ファイルの SHA-256 は Releases のページに載せています。ダウンロードしたファイルと一致するか確かめてから使ってください。

### Android

1. APK をダウンロードして開く
2. 「提供元不明のアプリ」のインストールを許可する
3. 初回起動時に yt-dlp を最新版へ自動更新します

### Windows

1. zip を好きな場所に展開し、`Otobako\Otobako.exe` を起動 (インストール不要)
2. 署名のないアプリのため、初回は「Windows によって PC が保護されました」と出ることがあります。
   「詳細情報」→「実行」で起動できます
3. 保留中のデータは `%LOCALAPPDATA%\Otobako` に置かれます。アンインストールはフォルダを消すだけです

## 使い方の注意 (必ずお読みください)

- 著作権者の許可なくアップロードされた音楽・動画と知りながらダウンロードすることは、
  日本では私的使用であっても違法です。権利者が公開を認めているものだけに使ってください
- 各サイトの利用規約を守ってください。ダウンロードを禁止しているサイトもあります
- 保存した曲を他人に配布・公開しないでください
- 本アプリは無保証です。利用によって生じたいかなる損害についても作者は責任を負いません

## ソースからのビルド

- Android: `powershell -File build.ps1 :app:assembleRelease` (Android SDK・JDK 17 以上が必要)
- Windows: `powershell -File desktop\fetch-tools.ps1` で同梱ツールを取得してから
  `powershell -File desktop\build.ps1` → `desktop\out\Otobako\Otobako.exe`

リリース署名の鍵はリポジトリに含めていません。鍵が無い場合、Android のリリースビルドは署名なしで出力されます。

## ライセンス

GNU General Public License v3.0 ([LICENSE](LICENSE))。
同梱しているソフトウェアとそのライセンスは [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。
