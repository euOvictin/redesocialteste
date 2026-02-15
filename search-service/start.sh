#!/bin/bash

# Start the search service
echo "Starting Search Service..."
uvicorn src.main:app --host 0.0.0.0 --port 8004
