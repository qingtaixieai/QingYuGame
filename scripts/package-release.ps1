$ErrorActionPreference='Stop'
$gameRoot=Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $gameRoot 'frontend')
try { npm ci; if($LASTEXITCODE -ne 0){throw 'npm ci failed'}; npm run build; if($LASTEXITCODE -ne 0){throw 'Frontend build failed'} } finally { Pop-Location }
Push-Location (Join-Path $gameRoot 'backend')
try { mvn -q verify; if($LASTEXITCODE -ne 0){throw 'Backend verification failed'} } finally { Pop-Location }
$releaseDir=Join-Path $gameRoot '.local/release'
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
Copy-Item -LiteralPath (Join-Path $gameRoot 'backend/target/world-0.1.0.jar') -Destination (Join-Path $releaseDir 'game.jar')
Copy-Item -LiteralPath (Join-Path $gameRoot 'deploy/compose.yaml') -Destination $releaseDir
Copy-Item -LiteralPath (Join-Path $gameRoot 'deploy/nginx.conf.template') -Destination $releaseDir
New-Item -ItemType Directory -Force -Path (Join-Path $releaseDir 'frontend') | Out-Null
Copy-Item -Path (Join-Path $gameRoot 'frontend/dist/*') -Destination (Join-Path $releaseDir 'frontend') -Recurse -Force
Copy-Item -LiteralPath (Join-Path $gameRoot 'deploy') -Destination $releaseDir -Recurse -Force
Copy-Item -LiteralPath (Join-Path $gameRoot 'README.md'),(Join-Path $gameRoot 'VERIFICATION.md') -Destination $releaseDir -Force
tar -czf (Join-Path $gameRoot '.local/qingyu-release.tar.gz') -C $releaseDir game.jar compose.yaml nginx.conf.template frontend deploy README.md VERIFICATION.md
if($LASTEXITCODE -ne 0){throw 'Packaging failed'}
Write-Output 'Release ready in world-game/.local/qingyu-release.tar.gz; no secrets or local database included.'
