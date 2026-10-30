@echo off
rem Builds the release package with the C# compiler that ships with Windows (.NET Framework 4):
rem   dist\MXO_QoL_Patch\MXO_QoL_Installer.exe + dist\MXO_QoL_Patch\data\...   and   dist\MXO_QoL_Patch.zip
setlocal
cd /d "%~dp0"
set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
set PKG=dist\MXO_QoL_Patch
if exist dist rmdir /s /q dist
mkdir %PKG%\data
"%CSC%" /nologo /target:winexe /optimize+ /platform:anycpu /win32manifest:src\app.manifest ^
  /out:%PKG%\MXO_QoL_Installer.exe /reference:System.Windows.Forms.dll /reference:System.Drawing.dll ^
  src\Installer.cs
if errorlevel 1 exit /b 1
copy /y data\* %PKG%\data\ >nul
powershell -NoProfile -Command "Compress-Archive -Path '%PKG%' -DestinationPath 'dist\MXO_QoL_Patch.zip' -Force"
if errorlevel 1 exit /b 1
echo Built %PKG%\MXO_QoL_Installer.exe and dist\MXO_QoL_Patch.zip
