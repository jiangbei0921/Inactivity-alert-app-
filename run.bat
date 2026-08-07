@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=C:\Program Files\Android\Android Studio\jbr\bin;%PATH%;C:\Users\ZBZ20\AppData\Local\Android\sdk\platform-tools"
echo ============================================
echo   compile and install
echo ============================================
call gradlew.bat installDebug
if %errorlevel% neq 0 (
    echo BUILD FAILED
    pause
    exit /b 1
)
echo.
echo starting app...
adb shell am start -n com.sitbreak.app/.MainActivity
if %errorlevel% neq 0 (
    echo launch failed, check USB connection
)
echo.
echo ============================================
echo   DONE
echo ============================================
pause