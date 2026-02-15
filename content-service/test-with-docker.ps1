# PowerShell script to run tests with Docker containers for dependencies

Write-Host "Starting test dependencies with Docker Compose..." -ForegroundColor Green

# Start only the required services for testing
docker-compose -f ../docker-compose.yml up -d postgres mongodb redis kafka

Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host "Running tests..." -ForegroundColor Green
mvn clean test

$TestExitCode = $LASTEXITCODE

Write-Host "Stopping test dependencies..." -ForegroundColor Yellow
docker-compose -f ../docker-compose.yml down

exit $TestExitCode
