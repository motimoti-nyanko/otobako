# Gradle breaks under a non-ASCII path, so mirror the sources to C:\Users\<user>\otobako
# and build there. Always edit the files on the original (Japanese) path.
# Usage: powershell -File build.ps1 [gradle tasks...]  (default :app:assembleDebug)
#        powershell -File build.ps1 :app:assembleRelease  -> out\Otobako-<version>.apk (release-signed)
param([string[]]$Tasks = @(':app:assembleDebug'))

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

$src = $PSScriptRoot
$dst = "$env:USERPROFILE\otobako"

if (Test-Path "$dst\app\src") { Remove-Item "$dst\app\src" -Recurse -Force }
New-Item -ItemType Directory -Force "$dst\app" | Out-Null
Copy-Item "$src\app\src" "$dst\app\" -Recurse -Force
Copy-Item "$src\app\build.gradle.kts" "$dst\app\" -Force
foreach ($f in 'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties') {
    Copy-Item "$src\$f" $dst -Force
}

Set-Location $dst
& "$env:USERPROFILE\.gradle\manual\gradle-8.13\bin\gradle.bat" --no-daemon --console=plain @Tasks
$code = $LASTEXITCODE

if ($code -eq 0) {
    New-Item -ItemType Directory -Force "$src\out" | Out-Null
    if ($Tasks -match 'Release') {
        $apk = "$dst\app\build\outputs\apk\release\app-release.apk"
        $ver = (Select-String -Path "$src\app\build.gradle.kts" -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
        $out = "$src\out\Otobako-$ver.apk"
    } else {
        $apk = "$dst\app\build\outputs\apk\debug\app-debug.apk"
        $out = "$src\out\Otobako-debug.apk"
    }
    if (Test-Path $apk) {
        Copy-Item $apk $out -Force
        Write-Host ''
        Write-Host "APK: $out"
    }
}
exit $code
