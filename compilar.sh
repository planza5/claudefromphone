#!/bin/bash

# Script de compilación
# Ubicación en pod: /workspace/compilar.sh

echo "=========================================="
echo "  COMPILAR APK"
echo "=========================================="

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Cargar entorno (Java, Android SDK, Gradle, etc.)
echo -e "${YELLOW}Cargando entorno...${NC}"
source /workspace/setup_env.sh

# Directorio del proyecto (usa el argumento o el default)
PROJECT_DIR="${1:-/workspace/claudefromphone}"

echo -e "${YELLOW}Directorio del proyecto: $PROJECT_DIR${NC}"

# Verificar que existe el proyecto
if [ ! -f "$PROJECT_DIR/gradlew" ]; then
    echo -e "${RED}Error: No se encuentra gradlew en $PROJECT_DIR${NC}"
    exit 1
fi

cd "$PROJECT_DIR"

# Verificar herramientas
echo -e "${YELLOW}Verificando herramientas...${NC}"
echo "JAVA_HOME: $JAVA_HOME"
echo "ANDROID_HOME: $ANDROID_HOME"
java -version 2>&1 | head -1
gradle -version 2>&1 | head -1

# Compilar
echo ""
echo -e "${YELLOW}Compilando APK...${NC}"
./gradlew assembleDebug --no-daemon

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Falló la compilación${NC}"
    exit 1
fi

# Ruta del APK generado
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Error: No se encuentra el APK en $APK_PATH${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=========================================="
echo -e "  BUILD SUCCESSFUL"
echo -e "==========================================${NC}"
echo -e "${GREEN}APK: $APK_PATH${NC}"
