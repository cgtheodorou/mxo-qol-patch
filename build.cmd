@echo off
rem Builds dist\MXO_QoL_Installer.exe with the C# compiler that ships with Windows (.NET Framework 4).
rem   build.cmd        release build (asks for administrator rights when run)
rem   build.cmd test   same, without the administrator prompt (for scripted testing)
setlocal
cd /d "%~dp0"
set CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe
if not exist "%CSC%" set CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe
set OUT=dist\MXO_QoL_Installer.exe
set MANIFEST=/win32manifest:src\app.manifest
if /i "%1"=="test" (
  set OUT=dist\MXO_QoL_Installer_test.exe
  set MANIFEST=/win32manifest:src\test.manifest
)
if not exist dist mkdir dist
"%CSC%" /nologo /target:winexe /optimize+ /platform:anycpu %MANIFEST% /out:%OUT% ^
  /reference:System.Windows.Forms.dll /reference:System.Drawing.dll ^
  /resource:data\client_qol.patch,client_qol.patch ^
  /resource:data\dxv9.dll,dxv9.dll ^
  /resource:data\dxvk.conf,dxvk.conf ^
  /resource:data\DXVK_LICENSE.txt,DXVK_LICENSE.txt ^
  /resource:data\mxoperf.dll,mxoperf.dll ^
  /resource:data\mxoperf.ini,mxoperf.ini ^
  src\Installer.cs
if errorlevel 1 exit /b 1
echo Built %OUT%
