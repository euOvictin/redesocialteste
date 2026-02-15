import pytest
from fastapi.testclient import TestClient
from src.main import app

client = TestClient(app)


def test_health_check():
    """Test health check endpoint."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "recommendation-engine"


def test_api_routes_registered():
    """Test that API routes are registered."""
    # Check that the app has routes
    routes = [route.path for route in app.routes]
    
    # Verify key endpoints exist
    assert "/health" in routes
    assert "/api/v1/feed/{user_id}" in routes
    assert "/api/v1/trending" in routes
    assert "/api/v1/score" in routes
    assert "/api/v1/invalidate-cache/{user_id}" in routes
