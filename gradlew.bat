@rem
@rem Gradle startup script for Windows
@rem
@rem Configure the Gradle wrapper

@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve "." and ".."
set APP_HOME=%APP_HOME:"=%

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Download Gradle wrapper jar if not present
set GRADLEW_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if not exist "%GRADLEW_JAR%" (
    echo Gradle wrapper jar not found. Please run 'gradle wrapper' first or install Android Studio.
    goto fail
)

@rem Setup the command line
set CLASSPATH=%GRADLEW_JAR%

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %OS%"=="Windows_NT" endlocal

:omega
exit /b %ERRORLEVEL%

:fail
echo.
echo ERROR: Gradle wrapper not properly configured.
echo Please install Android Studio or run 'gradle wrapper' from command line.
echo.
exit /b 1