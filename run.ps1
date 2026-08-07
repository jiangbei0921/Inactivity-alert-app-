$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$adb = "C:\Users\ZBZ20\AppData\Local\Android\sdk\platform-tools\adb.exe"

Write-Host "Building..." -ForegroundColor Cyan
.\gradlew.bat installDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "BUILD SUCCESS, launching app..." -ForegroundColor Green
    & $adb shell am start -n com.sitbreak.app/.MainActivity
} else {
    Write-Host "BUILD FAILED" -ForegroundColor Red
}