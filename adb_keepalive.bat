@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   ADB WiFi Keep-Alive Anti-Suspend
echo ========================================
echo.

:: Pedir IP del dispositivo (con valor por defecto)
set /p DEVICE_IP="Introduce la IP del dispositivo [100.82.217.8]: "
if "%DEVICE_IP%"=="" set DEVICE_IP=100.82.217.8

:: Pedir puerto (con valor por defecto)
set /p DEVICE_PORT="Introduce el puerto [5555]: "
if "%DEVICE_PORT%"=="" set DEVICE_PORT=5555

set DEVICE=%DEVICE_IP%:%DEVICE_PORT%

echo.
echo Conectando a %DEVICE%...
adb connect %DEVICE%

timeout /t 2 /nobreak >nul

echo.
echo === Configurando keep-alive anti-suspend ===
echo.

echo [1/4] Adquiriendo wakelock...
adb -s %DEVICE% shell "echo adb_persistent > /sys/power/wake_lock"

echo [2/4] Deshabilitando auto-suspend...
adb -s %DEVICE% shell "dumpsys deviceidle disable"

echo [3/4] Configurando WiFi siempre activo...
adb -s %DEVICE% shell "settings put global wifi_sleep_policy 2"
adb -s %DEVICE% shell "settings put global wifi_scan_throttle_enabled 0"

echo [4/4] Iniciando logcat streaming...
echo.

:: Crear archivo temporal para el log
set LOGFILE=%TEMP%\adb_keepalive_log.txt

:: Iniciar logcat en una nueva ventana minimizada
start "ADB Logcat Keep-Alive" /min adb -s %DEVICE% logcat -v time *:W

echo.
echo ========================================
echo   KEEP-ALIVE ACTIVO
echo ========================================
echo.
echo   Dispositivo: %DEVICE%
echo   Wakelock: ACTIVO
echo   Auto-suspend: DESHABILITADO
echo   Logcat: CORRIENDO (ventana minimizada)
echo.
echo   La conexion ADB deberia mantenerse estable.
echo.
echo ========================================
echo.
echo Presiona cualquier tecla para DETENER el keep-alive...
pause >nul

:: Cleanup
echo.
echo Deteniendo keep-alive...

echo [1/3] Liberando wakelock...
adb -s %DEVICE% shell "echo adb_persistent > /sys/power/wake_unlock"

echo [2/3] Re-habilitando auto-suspend...
adb -s %DEVICE% shell "dumpsys deviceidle enable"

echo [3/3] Cerrando logcat...
taskkill /FI "WindowTitle eq ADB Logcat Keep-Alive*" /F >nul 2>&1

echo.
echo Keep-alive detenido correctamente.
timeout /t 2 /nobreak >nul
