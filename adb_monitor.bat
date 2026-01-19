@echo off
:loop
adb devices
timeout /t 10 /nobreak >nul
goto loop
