#!/bin/bash

echo "Building and testing User Service with Docker..."

# Build and run tests in Docker container
docker run --rm \
  -v "$(pwd)":/app \
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn clean test

echo "Tests completed!"
