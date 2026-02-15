from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""
    
    # Server Configuration
    port: int = 8005
    host: str = "0.0.0.0"
    log_level: str = "info"
    
    # PostgreSQL Configuration
    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "redesocial"
    postgres_user: str = "postgres"
    postgres_password: str = "postgres"
    
    # Redis Configuration
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: Optional[str] = None
    
    # Kafka Configuration
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_group_id: str = "recommendation-engine"
    kafka_auto_offset_reset: str = "earliest"
    
    # Cache Configuration
    feed_cache_ttl: int = 300  # 5 minutes
    score_cache_ttl: int = 3600  # 1 hour
    
    # Feed Configuration
    posts_per_page: int = 20
    max_feed_size: int = 1000
    
    # Recommendation Configuration
    engagement_weight_likes: float = 1.0
    engagement_weight_comments: float = 2.0
    engagement_weight_shares: float = 3.0
    time_decay_hours: int = 24
    
    @property
    def postgres_url(self) -> str:
        """Get PostgreSQL connection URL."""
        return f"postgresql://{self.postgres_user}:{self.postgres_password}@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
    
    @property
    def redis_url(self) -> str:
        """Get Redis connection URL."""
        if self.redis_password:
            return f"redis://:{self.redis_password}@{self.redis_host}:{self.redis_port}/{self.redis_db}"
        return f"redis://{self.redis_host}:{self.redis_port}/{self.redis_db}"
    
    class Config:
        env_file = ".env"
        case_sensitive = False


settings = Settings()
