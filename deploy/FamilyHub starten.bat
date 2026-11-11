@echo off
rem Windows: Doppelklick startet FamilyHub (siehe familyhub.ps1). Das Fenster bleibt offen.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0familyhub.ps1" start
echo.
pause
