$ErrorActionPreference='Stop'
$gameRoot=Split-Path $PSScriptRoot -Parent
$devSettings=Get-Content -LiteralPath (Join-Path $gameRoot '.local/dev.json') | ConvertFrom-Json
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:55432/worldgame'
$env:DATABASE_USER='worldgame'
$env:DATABASE_PASSWORD='local-only'
$env:ADMIN_USERNAME=$devSettings.username
$env:ADMIN_PASSWORD=$devSettings.password
$env:APP_ORIGIN='http://127.0.0.1:5173'
$env:SECURE_COOKIE='false'
Set-Location (Join-Path $gameRoot 'backend')
mvn -q spring-boot:run
