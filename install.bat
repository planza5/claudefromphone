@echo off
setlocal enabledelayedexpansion

set PACKAGE=com.example.runpodmanager
set ACTIVITY=.MainActivity

echo ============================================
echo  Instalando APK
echo ============================================
echo.

REM Guardar dispositivos en array
set count=0
for /f "skip=1 tokens=1,2" %%a in ('adb devices -l') do (
    if not "%%a"=="" (
        set /a count+=1
        set "device[!count!]=%%a"
        set "info[!count!]=%%b"
    )
)

if %count%==0 (
    echo [ERROR] No hay dispositivos conectados
    pause
    exit /b 1
)

if %count%==1 (
    set "selected=!device[1]!"
    echo Dispositivo: !selected!
) else (
    echo Dispositivos conectados:
    echo.
    for /l %%i in (1,1,%count%) do (
        echo   %%i. !device[%%i]! !info[%%i]!
    )
    echo.
    set /p choice="Elige dispositivo (1-%count%): "
    for %%c in (!choice!) do set "selected=!device[%%c]!"
)

echo.

REM Parar la app si esta corriendo
echo [1/3] Deteniendo app...
adb -s !selected! shell am force-stop %PACKAGE% 2>nul

REM Instalar
echo [2/3] Instalando...
adb -s !selected! install -r "%~dp0app\build\outputs\apk\debug\app-debug.apk"

if errorlevel 1 (
    echo.
    echo [ERROR] Fallo la instalacion
    pause
    exit /b 1
)

REM Ejecutar
echo [3/3] Ejecutando...
adb -s !selected! shell am start -n %PACKAGE%/%ACTIVITY%

echo.
echo [OK] Completado
pause
