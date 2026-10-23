# Downloads the external tools bundled with the Windows build into desktop\vendor\bin
# and a JDK with jpackage into %USERPROFILE%\.jdks\temurin-21 (only if missing).
# Usage: powershell -File fetch-tools.ps1
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$bin = "$root\vendor\bin"
$dl = "$root\vendor\download"
New-Item -ItemType Directory -Force $bin, $dl | Out-Null

function Get-File($url, $out) {
    if (-not (Test-Path $out)) { Write-Host "download $url"; Invoke-WebRequest $url -OutFile $out -UseBasicParsing }
}

# yt-dlp (single exe, self-updating)
Get-File 'https://github.com/yt-dlp/yt-dlp/releases/download/2026.10.21/yt-dlp.exe' "$bin\yt-dlp.exe"

# ffmpeg / ffprobe (shared build: small exes + DLLs)
$ff = "$dl\ffmpeg.zip"
Get-File 'https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.1-latest-win64-gpl-shared-8.1.zip' $ff
if (-not (Test-Path "$bin\ffmpeg.exe")) {
    $x = "$dl\ffmpeg"
    Expand-Archive $ff $x -Force
    $b = Get-ChildItem $x -Recurse -Directory -Filter bin | Select-Object -First 1
    Copy-Item "$($b.FullName)\ffmpeg.exe", "$($b.FullName)\ffprobe.exe" $bin
    Copy-Item "$($b.FullName)\*.dll" $bin
}

# deno (JavaScript runtime that yt-dlp needs for YouTube)
$dz = "$dl\deno.zip"
Get-File 'https://github.com/denoland/deno/releases/download/v2.9.8/deno-x86_64-pc-windows-msvc.zip' $dz
if (-not (Test-Path "$bin\deno.exe")) { Expand-Archive $dz $bin -Force }

# JDK 21 with jpackage (Android Studio's JBR lacks it)
$jdk = "$env:USERPROFILE\.jdks\temurin-21"
if (-not (Test-Path "$jdk\bin\jpackage.exe")) {
    $jz = "$dl\jdk.zip"
    Get-File 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip' $jz
    $jx = "$dl\jdk"
    Expand-Archive $jz $jx -Force
    $top = Get-ChildItem $jx -Directory | Select-Object -First 1
    New-Item -ItemType Directory -Force "$env:USERPROFILE\.jdks" | Out-Null
    Move-Item $top.FullName $jdk
}
Get-ChildItem $bin | Select-Object Name, Length | Format-Table -AutoSize
Write-Host "JDK: $jdk"
