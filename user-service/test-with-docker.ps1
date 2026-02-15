Write-Host "Building and testing User Service with Docker..." -ForegroundColor Green

# Build and run tests in Docker container
docker run --rm `
  -v "${PWD}:/app" `
  -w /app `
  maven:3.9-eclipse-temurin-17 `
  mvn clean test

Write-Host "Tests completed!" -ForegroundColor Green
