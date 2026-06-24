# Script para compilar la app, generar metadatos de actualización y publicarla en GitHub.
# Ejecutar desde PowerShell en la raíz del proyecto.

$ErrorActionPreference = "Stop"

# Configurar codificación UTF-8 para evitar caracteres corruptos en consola Windows/PowerShell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# 1. Configurar JAVA_HOME para Android Studio JDK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Write-Host "Compilando la aplicación en modo Debug..." -ForegroundColor Cyan

# 2. Compilar APK
./gradlew.bat assembleDebug

# 3. Crear directorio de release si no existe
$releaseDir = Join-Path $PSScriptRoot "release"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir | Out-Null
}

# 4. Copiar APK compilada al directorio de release
$sourceApk = Join-Path $PSScriptRoot "app/build/outputs/apk/debug/app-debug.apk"
$targetApk = Join-Path $releaseDir "ona-miciclo-latest.apk"
Copy-Item -Path $sourceApk -Destination $targetApk -Force
Write-Host "APK copiada a: $targetApk" -ForegroundColor Green

# 5. Extraer versión de app/build.gradle.kts
$gradleFile = Join-Path $PSScriptRoot "app/build.gradle.kts"
$gradleContent = Get-Content $gradleFile -Raw

$versionCodeMatch = [regex]::Match($gradleContent, 'versionCode\s*=\s*(\d+)')
$versionNameMatch = [regex]::Match($gradleContent, 'versionName\s*=\s*"([^"]+)"')

if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
    Write-Error "No se pudo extraer la versión de build.gradle.kts"
}

$versionCode = [int]$versionCodeMatch.Groups[1].Value
$versionName = $versionNameMatch.Groups[1].Value

Write-Host "Versión detectada: $versionName (Código: $versionCode)" -ForegroundColor Cyan

# 6. Crear archivo update.json con los metadatos
# NOTA: Usamos el repo del usuario para la URL de descarga directa
$repoUrl = "https://raw.githubusercontent.com/angelaramiz/Ona-MiCiclo.Local/main/release/ona-miciclo-latest.apk"

$updateMetadata = @{
    versionCode = $versionCode
    versionName = $versionName
    downloadUrl = $repoUrl
}

$jsonFile = Join-Path $releaseDir "update.json"
$updateMetadata | ConvertTo-Json | Out-File -FilePath $jsonFile -Encoding utf8
Write-Host "Archivo de metadatos de actualización creado: $jsonFile" -ForegroundColor Green

# 7. Git commit y push
Write-Host "Subiendo cambios a GitHub..." -ForegroundColor Cyan
git add "release/ona-miciclo-latest.apk" "release/update.json"
git commit -m "Auto-release: Versión $versionName (Build $versionCode)"
git push origin main

Write-Host "¡Publicación completada con éxito!" -ForegroundColor Green
