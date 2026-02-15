try:
    import psycopg2
    from psycopg2.extras import RealDictCursor
    PSYCOPG2_AVAILABLE = True
except ImportError:
    PSYCOPG2_AVAILABLE = False
    psycopg2 = None
    RealDictCursor = None
    
from contextlib import contextmanager
from typing import Generator
import redis
from src.config import settings


class Database:
    """PostgreSQL database connection manager."""
    
    def __init__(self):
        self.connection_params = {
            'host': settings.postgres_host,
            'port': settings.postgres_port,
            'database': settings.postgres_db,
            'user': settings.postgres_user,
            'password': settings.postgres_password,
        }
    
    def _check_available(self):
        """Check if psycopg2 is available."""
        if not PSYCOPG2_AVAILABLE:
            raise ImportError("psycopg2-binary is required for database connections")
    
    @contextmanager
    def get_connection(self) -> Generator:
        """Get a database connection with automatic cleanup."""
        self._check_available()
        conn = psycopg2.connect(**self.connection_params)
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()
    
    @contextmanager
    def get_cursor(self) -> Generator:
        """Get a database cursor with automatic cleanup."""
        with self.get_connection() as conn:
            cursor = conn.cursor(cursor_factory=RealDictCursor)
            try:
                yield cursor
            finally:
                cursor.close()


class RedisCache:
    """Redis cache connection manager."""
    
    def __init__(self):
        self.client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            db=settings.redis_db,
            password=settings.redis_password,
            decode_responses=True
        )
    
    def get(self, key: str) -> str | None:
        """Get value from cache."""
        return self.client.get(key)
    
    def set(self, key: str, value: str, ttl: int | None = None) -> bool:
        """Set value in cache with optional TTL."""
        if ttl:
            return self.client.setex(key, ttl, value)
        return self.client.set(key, value)
    
    def delete(self, key: str) -> int:
        """Delete key from cache."""
        return self.client.delete(key)
    
    def delete_pattern(self, pattern: str) -> int:
        """Delete all keys matching pattern."""
        keys = self.client.keys(pattern)
        if keys:
            return self.client.delete(*keys)
        return 0
    
    def exists(self, key: str) -> bool:
        """Check if key exists in cache."""
        return self.client.exists(key) > 0


# Global instances
db = Database()
cache = RedisCache()
