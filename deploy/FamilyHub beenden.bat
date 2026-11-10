@echo off
rem Windows: Doppelklick beendet FamilyHub (siehe familyhub.ps1). Die Daten bleiben erhalten.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0familyhub.ps1" stop
echo.
pause
