"""Configuration for Notification Service."""
from pydantic_settings import BaseSettings
from pydantic import ConfigDict


class Settings(BaseSettings):
    """Application settings."""
    model_config = ConfigDict(env_file=".env", case_sensitive=False, extra='ignore')
    
    port: int = 8001
    host: str = "0.0.0.0"
    log_level: str = "info"
    
    # MongoDB
    mongo_uri: str = "mongodb://admin:admin@localhost:27017/rede_social?authSource=admin"
    mongo_db_name: str = "rede_social"
    
    # Kafka
    kafka_bootstrap_servers: str = "localhost:9093"
    kafka_content_topic: str = "content.events"
    kafka_social_topic: str = "social.events"
    kafka_consumer_group: str = "notification-service"
    
    # JWT (for API auth)
    jwt_secret: str = "your-super-secret-jwt-key-change-in-production"
    
    # FCM (Firebase Cloud Messaging)
    fcm_server_key: str = ""
    fcm_project_id: str = ""
    
    # APNs (Apple Push Notification)
    apns_key_id: str = ""
    apns_team_id: str = ""
    apns_bundle_id: str = "com.redesocial.app"
    apns_key_file: str = ""
    
    # Aggregation window (minutes)
    comment_aggregation_minutes: int = 5
    
    # Retention (days)
    notification_retention_days: int = 90


settings = Settings()
