# SOLO PARA DESARROLLO LOCAL.
#
# Arranca el servicio de identidad en este equipo contra el PostgreSQL de compose.yaml.
#
# Que hace:
#   1. Lee el archivo .env de la raiz del repositorio.
#   2. Pone las variables IDENTIDAD_* solo en esta ventana de PowerShell (no quedan
#      guardadas en el sistema).
#   3. Empaqueta el servicio sin ejecutar las pruebas y lo arranca en el puerto 8080.
#
# Uso, desde la raiz del repositorio:   ./infra/local/arrancar-identidad.ps1
# Para detener el servicio:             Ctrl + C
#
# Nota: este archivo no lleva tildes a proposito, para que Windows PowerShell 5.1 lo lea
# bien sea cual sea la codificacion con la que se guarde.

$ErrorActionPreference = 'Stop'

$raiz = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$archivoEnv = Join-Path $raiz '.env'

if (-not (Test-Path $archivoEnv)) {
    Write-Error "No existe $archivoEnv. Copia .env.ejemplo como .env y cambia las contrasenas."
}

# Cada linea util del .env tiene la forma NOMBRE=valor. Se ignoran comentarios y lineas vacias.
foreach ($linea in Get-Content $archivoEnv -Encoding UTF8) {
    $texto = $linea.Trim()
    if ($texto -eq '' -or $texto.StartsWith('#')) { continue }

    $separador = $texto.IndexOf('=')
    if ($separador -lt 1) { continue }

    $nombre = $texto.Substring(0, $separador).Trim()
    $valor = $texto.Substring($separador + 1).Trim()

    if ($nombre -like 'IDENTIDAD_*') {
        if ($valor -eq 'cambiar-esto' -or $valor -eq '') {
            Write-Error "La variable $nombre de .env sigue sin un valor propio."
        }
        Set-Item -Path "Env:$nombre" -Value $valor
    }
}

Push-Location $raiz
try {
    # Empaqueta el servicio y las librerias compartidas de las que depende. Las pruebas no
    # se ejecutan aqui; para eso esta ./mvnw verify.
    & ./mvnw -q -pl svc-identidad -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { Write-Error 'No se pudo empaquetar el servicio.' }

    $jar = Get-ChildItem 'svc-identidad/target' -Filter 'svc-identidad-*.jar' |
        Where-Object { $_.Name -notlike '*.original' } |
        Select-Object -First 1
    if ($null -eq $jar) { Write-Error 'No se encontro el archivo jar del servicio.' }

    & java -jar $jar.FullName
}
finally {
    Pop-Location
}
