# Recommendation Engine

Personalized feed generation and content recommendation service for Rede Social Brasileira.

## Features

- **Relevance Score Calculation**: Calculate post relevance based on engagement metrics
- **Personalized Feed**: Generate customized feeds for users based on followed accounts
- **Trending Posts**: Provide popular content for users without followers
- **Cache Management**: Efficient caching with automatic invalidation
- **Event-Driven Updates**: Real-time score updates from interaction events

## Technology Stack

- **Framework**: FastAPI
- **Database**: PostgreSQL (training data), Redis (caching)
- **Message Broker**: Apache Kafka
- **ML Library**: scikit-learn
- **Testing**: pytest, Hypothesis (property-based testing)

## Setup

### Prerequisites

- Python 3.11+
- PostgreSQL
- Redis
- Apache Kafka

### Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Configure environment variables:
```bash
cp .env.example .env
# Edit .env with your configuration
```

3. Run the service:
```bash
python -m src.main
```

The service will be available at `http://localhost:8005`

### Docker

Build and run with Docker:
```bash
docker build -t recommendation-engine .
docker run -p 8005:8005 --env-file .env recommendation-engine
```

## API Endpoints

### Get Personalized Feed
```
GET /api/v1/feed/{user_id}?cursor={cursor}&limit={limit}
```

Returns personalized feed with posts from followed users, ordered by relevance.

### Calculate Relevance Score
```
POST /api/v1/score
{
  "user_id": "string",
  "post_id": "string"
}
```

Calculate relevance score for a specific post and user.

### Get Trending Posts
```
GET /api/v1/trending?limit={limit}
```

Get popular posts for users without followers.

### Invalidate Cache
```
POST /api/v1/invalidate-cache/{user_id}
```

Manually invalidate feed cache for a user.

## Configuration

Key configuration options in `.env`:

- `POSTS_PER_PAGE`: Number of posts per page (default: 20)
- `FEED_CACHE_TTL`: Feed cache TTL in seconds (default: 300)
- `ENGAGEMENT_WEIGHT_LIKES`: Weight for likes in score calculation
- `ENGAGEMENT_WEIGHT_COMMENTS`: Weight for comments in score calculation
- `ENGAGEMENT_WEIGHT_SHARES`: Weight for shares in score calculation
- `TIME_DECAY_HOURS`: Hours for time decay in relevance calculation

## Testing

Run tests:
```bash
pytest
```

Run property-based tests:
```bash
pytest tests/ -v -k property
```

## Architecture

The Recommendation Engine:
1. Consumes events from Kafka (post.created, like.created, etc.)
2. Calculates relevance scores based on engagement metrics
3. Caches pre-computed feeds in Redis
4. Invalidates cache when new posts are created
5. Provides trending posts for discovery

## Relevance Score Algorithm

Score = (likes × W_likes + comments × W_comments + shares × W_shares) × time_decay

Where:
- W_likes, W_comments, W_shares are configurable weights
- time_decay = e^(-hours_since_post / TIME_DECAY_HOURS)
