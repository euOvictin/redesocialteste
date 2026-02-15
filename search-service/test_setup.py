"""Simple test to verify the search service setup"""
import sys
import os

# Add src to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'src'))

from fastapi.testclient import TestClient
from src.main import app

client = TestClient(app)


def test_root_endpoint():
    """Test root endpoint"""
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["service"] == "search-service"
    assert data["version"] == "1.0.0"
    assert data["status"] == "running"
    print("✓ Root endpoint test passed")


def test_health_endpoint():
    """Test health check endpoint"""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert data["service"] == "search-service"
    print("✓ Health endpoint test passed")


if __name__ == "__main__":
    print("Running search service setup tests...")
    try:
        test_root_endpoint()
        test_health_endpoint()
        print("\n✅ All tests passed!")
    except AssertionError as e:
        print(f"\n❌ Test failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Error: {e}")
        sys.exit(1)
