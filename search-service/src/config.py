from pydantic_settings import BaseSettings
from pydantic import ConfigDict


class Settings(BaseSettings):
    """Application settings"""
    model_config = ConfigDict(env_file=".env", case_sensitive=False, extra='ignore')
    
    elasticsearch_host: str = "localhost"
    elasticsearch_port: int = 9200
    port: int = 8004
    
    # Kafka settings
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_content_topic: str = "content.events"
    kafka_user_topic: str = "user.events"
    kafka_consumer_group: str = "search-service"
    
    @property
    def elasticsearch_url(self) -> str:
        return f"http://{self.elasticsearch_host}:{self.elasticsearch_port}"


settings = Settings()
