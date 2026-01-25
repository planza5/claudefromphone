#!/bin/bash

# Script para encontrar proyectos Android en un directorio
# Uso: ./find_android_projects.sh [directorio]

search_dir="${1:-.}"

if [[ ! -d "$search_dir" ]]; then
    echo "Error: '$search_dir' no es un directorio válido" >&2
    exit 1
fi

# Buscar proyectos Android por la presencia de archivos característicos
find "$search_dir" -type f \( -name "settings.gradle" -o -name "settings.gradle.kts" \) 2>/dev/null | while read -r settings_file; do
    project_dir=$(dirname "$settings_file")

    # Verificar que sea un proyecto Android (tiene build.gradle con plugin android o app/build.gradle)
    if [[ -f "$project_dir/build.gradle" || -f "$project_dir/build.gradle.kts" ]]; then
        # Buscar indicadores de proyecto Android
        if grep -rq "com.android" "$project_dir"/*.gradle* 2>/dev/null || \
           [[ -d "$project_dir/app" && (-f "$project_dir/app/build.gradle" || -f "$project_dir/app/build.gradle.kts") ]]; then
            echo "$project_dir"
        fi
    fi
done | sort -u
