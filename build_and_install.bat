@echo off
setlocal enabledelayedexpansion

echo ============================================
echo  Android Build y Install - Verificacion
echo ============================================
echo.

REM Verificar JAVA_HOME
if not defined JAVA_HOME (
    echo [ERROR] JAVA_HOME no esta configurado
    echo Configuralo con: set JAVA_HOME=C:\ruta\a\tu\jdk
    pause
    exit /b 1
)
echo [OK] JAVA_HOME: %JAVA_HOME%

REM Verificar ANDROID_HOME
if not defined ANDROID_HOME (
    echo [ERROR] ANDROID_HOME no esta configurado
    echo Configuralo con: set ANDROID_HOME=C:\Users\TuUsuario\AppData\Local\Android\Sdk
    pause
    exit /b 1
)
echo [OK] ANDROID_HOME: %ANDROID_HOME%

REM Verificar gradlew existe
if not exist "%~dp0gradlew.bat" (
    echo [ERROR] gradlew.bat no encontrado en %~dp0
    echo Asegurate de ejecutar este script desde la raiz de tu proyecto Android
    pause
    exit /b 1
)
echo [OK] Gradle wrapper encontrado

REM Verificar ADB
where adb >nul 2>&1
if errorlevel 1 (
    echo [ADVERTENCIA] ADB no esta en el PATH
    echo Considera agregar %ANDROID_HOME%\platform-tools al PATH
) else (
    echo [OK] ADB disponible
)

echo.
echo ============================================
echo  Verificando Gradle Daemon
echo ============================================

REM Verificar estado del daemon
call "%~dp0gradlew.bat" --status
if errorlevel 1 (
    echo [INFO] Daemon no activo, se iniciara automaticamente
)

echo.
echo ============================================
echo  Optimizando dispositivo
echo ============================================

REM Desactivar animaciones para instalacion mas rapida
echo Desactivando animaciones del dispositivo para instalacion mas rapida...
adb shell settings put global window_animation_scale 0 >nul 2>&1
adb shell settings put global transition_animation_scale 0 >nul 2>&1
adb shell settings put global animator_duration_scale 0 >nul 2>&1
echo [OK] Animaciones desactivadas

echo.
echo ============================================
echo  Iniciando Build e Instalacion
echo ============================================
echo.
echo Iniciando build... [%TIME%]
echo (El log completo se guardara en gradle-build.log)
echo.

REM Ejecutar build guardando log completo
call "%~dp0gradlew.bat" installDebug --info > gradle-build.log 2>&1

REM Mostrar resumen de tareas ejecutadas
echo.
echo ========================================
echo  RESUMEN DE COMPILACION
echo ========================================
findstr /C:"EXECUTED" gradle-build.log > nul
if not errorlevel 1 (
    echo.
    echo Tareas que SI se recompilaron:
    findstr /C:"EXECUTED" gradle-build.log
)

findstr /C:"UP-TO-DATE" gradle-build.log > nul
if not errorlevel 1 (
    echo.
    echo Tareas que NO cambiaron ^(muestra las primeras 10^):
    findstr /C:"UP-TO-DATE" gradle-build.log | findstr /N "^" | findstr "^[1-9]:" | findstr /V "^[1-9][0-9]:"
)

findstr /C:"FROM-CACHE" gradle-build.log > nul
if not errorlevel 1 (
    echo.
    echo Tareas recuperadas de cache ^(muestra las primeras 5^):
    findstr /C:"FROM-CACHE" gradle-build.log | findstr /N "^" | findstr "^[1-5]:"
)
echo.
echo ========================================

REM Verificar si el build fue exitoso
findstr /C:"BUILD SUCCESSFUL" gradle-build.log > nul
if errorlevel 1 (
    echo.
    echo [ERROR] Build fallido [%TIME%]
    echo.
    echo Ultimas lineas del error:
    powershell -command "Get-Content gradle-build.log | Select-Object -Last 20"
    pause
    exit /b 1
)

echo.
echo [EXITO] App compilada e instalada correctamente [%TIME%]
echo.
echo TIP: Las siguientes ejecuciones seran mucho mas rapidas gracias al daemon
echo      y la compilacion incremental de Gradle
echo      Log completo disponible en: gradle-build.log
echo.
pause
