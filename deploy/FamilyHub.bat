@echo off
rem Windows: Doppelklick oeffnet das FamilyHub-Fenster mit "Starten" und "Beenden" (fenster\familyhub-fenster.ps1).
start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0fenster\familyhub-fenster.ps1"
