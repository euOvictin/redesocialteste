#!/bin/bash

# Script to run tests with Docker containers for dependencies

echo "Starting test dependencies with Docker Compose..."

# Start only the required services for testing
docker-compose -f ../docker-compose.yml up -d postgres mongodb redis kafka

echo "Waiting for services to be ready..."
sleep 10

echo "Running tests..."
mvn clean test

TEST_EXIT_CODE=$?

echo "Stopping test dependencies..."
docker-compose -f ../docker-compose.yml down

exit $TEST_EXIT_CODE
