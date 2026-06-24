# Script para compilar la app, generar metadatos de actualizacion y publicarla en GitHub.
# Ejecutar desde PowerShell en la raiz del proyecto.

$ErrorActionPreference = "Stop"

# Configurar codificacion UTF-8 para evitar caracteres corruptos en consola Windows/PowerShell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# 1. Extraer y actualizar version en app/build.gradle.kts (se ejecuta antes de compilar)
$gradleFile = Join-Path $PSScriptRoot "app/build.gradle.kts"
$gradleContent = Get-Content $gradleFile -Raw

$versionCodeMatch = [regex]::Match($gradleContent, 'versionCode\s*=\s*(\d+)')
$versionNameMatch = [regex]::Match($gradleContent, 'versionName\s*=\s*"([^"]+)"')

if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
    Write-Error "No se pudo extraer la version de build.gradle.kts"
}

$oldVersionCode = [int]$versionCodeMatch.Groups[1].Value
$oldVersionName = $versionNameMatch.Groups[1].Value

# Incrementar version
$versionCode = $oldVersionCode + 1

# Generar nuevo versionName incrementando el parche
if ($oldVersionName -match '^(\d+)\.(\d+)\.(\d+)(.*)$') {
    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = [int]$Matches[3] + 1
    $suffix = $Matches[4]
    $versionName = "${major}.${minor}.${patch}${suffix}"
} else {
    $versionName = $oldVersionName + ".1"
}

# Reemplazar en build.gradle.kts
$gradleContent = $gradleContent -replace "versionCode\s*=\s*$oldVersionCode", "versionCode = $versionCode"
$gradleContent = $gradleContent -replace 'versionName\s*=\s*\"' + [regex]::Escape($oldVersionName) + '\"', ('versionName = "' + $versionName + '"')
Set-Content -Path $gradleFile -Value $gradleContent

Write-Host "Version actualizada en Gradle: $versionName (Codigo: $versionCode)" -ForegroundColor Cyan

# 2. Configurar JAVA_HOME para Android Studio JDK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Write-Host "Compilando la aplicacion en modo Release..." -ForegroundColor Cyan

# 3. Compilar APK con la nueva version
./gradlew.bat assembleRelease

# 4. Crear directorio de release si no existe
$releaseDir = Join-Path $PSScriptRoot "release"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir | Out-Null
}

# 5. Copiar APK compilada al directorio de release
$sourceApk = Join-Path $PSScriptRoot "app/build/outputs/apk/release/app-release.apk"
$targetApk = Join-Path $releaseDir "ona-miciclo-latest.apk"
Copy-Item -Path $sourceApk -Destination $targetApk -Force
Write-Host "APK copiada a: $targetApk" -ForegroundColor Green

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
Write-Host "Archivo de metadatos de actualizacion creado: $jsonFile" -ForegroundColor Green

# 7. Git commit y push
Write-Host "Subiendo cambios a GitHub..." -ForegroundColor Cyan
git add "release/ona-miciclo-latest.apk" "release/update.json" "app/build.gradle.kts"
git commit -m "Auto-release: Version $versionName (Build $versionCode)"

try {
    git push origin main
    Write-Host "Cambios subidos a GitHub con exito." -ForegroundColor Green
} catch {
    Write-Warning "No se pudo realizar el push a GitHub (posible problema de permisos SSH). Los archivos se han compilado localmente."
}

Write-Host "Publicacion completada localmente." -ForegroundColor Green
