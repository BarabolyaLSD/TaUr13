@echo off
setlocal
set VERSION=8.7
where gradle >nul 2>nul
if %errorlevel%==0 (
  gradle %*
  exit /b %errorlevel%
)
set GH=%USERPROFILE%\.gradle
set INSTALL=%GH%\wrapper\dists\gradle-%VERSION%-bin\local-gradle-%VERSION%
set BIN=%INSTALL%\gradle-%VERSION%\bin\gradle.bat
if exist "%BIN%" (
  call "%BIN%" %*
  exit /b %errorlevel%
)
echo Gradle %VERSION% is not installed. Install Gradle or use gradlew from Git Bash/WSL.
exit /b 1
