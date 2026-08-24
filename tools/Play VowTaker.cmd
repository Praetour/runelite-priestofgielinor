@echo off
REM Double-clickable wrapper. Bypasses PowerShell execution policy for this one script only.
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0VowTaker-Launcher.ps1" %*
endlocal
