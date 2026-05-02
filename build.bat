@echo off
setlocal

set GRADLE_VERSION=4.9
set GRADLE_ZIP=gradle-%GRADLE_VERSION%-bin.zip
set GRADLE_URL=https://services.gradle.org/distributions/%GRADLE_ZIP%
set GRADLE_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin
set GRADLE_HOME=%GRADLE_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_EXE=%GRADLE_HOME%\bin\gradle.bat

if exist "%GRADLE_EXE%" goto run

echo [*] Gradle %GRADLE_VERSION% not found. Downloading...
if not exist "%GRADLE_DIR%" mkdir "%GRADLE_DIR%"

powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_DIR%\%GRADLE_ZIP%' }"
if %ERRORLEVEL% neq 0 (
    echo [!] Download failed. Check your internet connection.
    exit /b 1
)

echo [*] Extracting...
powershell -Command "& { Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%GRADLE_DIR%\%GRADLE_ZIP%', '%GRADLE_DIR%') }"
if %ERRORLEVEL% neq 0 (
    echo [!] Extraction failed.
    exit /b 1
)

echo [*] Gradle %GRADLE_VERSION% ready.

:run
echo [*] Running: gradle %*
"%GRADLE_EXE%" %*
