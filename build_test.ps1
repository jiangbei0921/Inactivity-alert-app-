$ErrorActionPreference = "Stop"
Write-Host "=== SitBreak Build Test ===" -ForegroundColor Green

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\ZBZ20\AppData\Local\Android\Sdk"

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"

Set-Location "d:\cs\jiuzuoapp"
.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n=== BUILD SUCCESSFUL ===" -ForegroundColor Green
    Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk"
} else {
    Write-Host "`n=== BUILD FAILED ===" -ForegroundColor Red
}