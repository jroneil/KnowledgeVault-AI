$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot

Write-Host "Validating Docker Compose configuration..."
docker compose --project-directory $repositoryRoot config --quiet

Write-Host "Compiling the AI service..."
Push-Location (Join-Path $repositoryRoot "ai-service")
try {
    python -m compileall -q app
}
finally {
    Pop-Location
}

Write-Host "Linting and building the frontend..."
Push-Location (Join-Path $repositoryRoot "frontend/knowledgevault-ui")
try {
    npm.cmd run lint
    npm.cmd run build
}
finally {
    Pop-Location
}

Write-Host "Testing the backend..."
Push-Location (Join-Path $repositoryRoot "backend/document-service")
try {
    cmd /c mvnw.cmd test
}
finally {
    Pop-Location
}

Write-Host "Baseline validation completed successfully."
