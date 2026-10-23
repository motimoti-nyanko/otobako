# Gradle breaks under a non-ASCII path, so mirror the sources to C:\Users\<user>\otobako-desktop
# and build there. Always edit the files on the original (Japanese) path.
# Usage: powershell -File build.ps1 [gradle tasks...]  (default: createDistributable)
# Result: desktop\out\Otobako\Otobako.exe (portable folder)
param([string[]]$Tasks = @('createDistributable'))

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\temurin-21"

$src = $PSScriptRoot
$dst = "$env:USERPROFILE\otobako-desktop"

if (Test-Path "$dst\src") { Remove-Item "$dst\src" -Recurse -Force }
New-Item -ItemType Directory -Force $dst | Out-Null
Copy-Item "$src\src" $dst -Recurse -Force
foreach ($f in 'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties') { Copy-Item "$src\$f" $dst -Force }
robocopy "$src\icon" "$dst\icon" /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
# 同梱ツールは差分だけ同期 (数百 MB あるため)
robocopy "$src\vendor\bin" "$dst\res\windows\bin" /MIR /NFL /NDL /NJH /NJS /NP | Out-Null

Set-Location $dst
& "$env:USERPROFILE\.gradle\manual\gradle-8.13\bin\gradle.bat" --no-daemon --console=plain @Tasks
$code = $LASTEXITCODE

$app = "$dst\build\compose\binaries\main\app\Otobako"
if ($code -eq 0 -and (Test-Path "$app\Otobako.exe")) {
    New-Item -ItemType Directory -Force "$src\out" | Out-Null
    robocopy $app "$src\out\Otobako" /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
    Write-Host ''
    Write-Host "APP: $src\out\Otobako\Otobako.exe"
}
exit $code
