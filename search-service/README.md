# Search Service

Search and indexing service for Rede Social Brasileira. Built with Python, FastAPI, and Elasticsearch.

## Features

- Full-text search for posts, users, and hashtags
- Fuzzy search to tolerate typos
- Autocomplete suggestions
- Real-time indexing via Kafka events
- High-performance search with Elasticsearch

## Technology Stack

- **Framework**: FastAPI
- **Search Engine**: Elasticsearch 8.11
- **Language**: Python 3.11

## Project Structure

```
search-service/
├── src/
│   ├── __init__.py
│   ├── main.py                 # FastAPI application
│   ├── config.py               # Configuration settings
│   ├── elasticsearch_client.py # Elasticsearch client
│   └── indices.py              # Index mappings and creation
├── Dockerfile
├── requirements.txt
├── .env.example
└── README.md
```

## Setup

### Prerequisites

- Python 3.11+
- Elasticsearch 8.11+

### Local Development

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Create `.env` file:
```bash
cp .env.example .env
```

3. Configure environment variables in `.env`:
```
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200
PORT=8004
```

4. Run the service:
```bash
uvicorn src.main:app --reload --port 8004
```

### Docker

Build and run with Docker:
```bash
docker build -t search-service .
docker run -p 8004:8004 --env-file .env search-service
```

### Docker Compose

Run with the full stack:
```bash
docker-compose up search-service
```

## API Endpoints

### Health Check
```
GET /health
```

Returns the health status of the service and Elasticsearch connection.

### Root
```
GET /
```

Returns basic service information.

### Search
```
GET /search?q={query}&type={type}&page={page}&page_size={size}
```

Search for content with fuzzy matching support.

**Query Parameters:**
- `q` (required): Search query (minimum 2 characters)
- `type` (optional): Filter by type - `posts`, `users`, or `hashtags`. Omit to search all types.
- `page` (optional): Page number (default: 1)
- `page_size` (optional): Results per page (default: 20, max: 100)

**Features:**
- Fuzzy matching tolerates 1-2 character typos
- Returns results in less than 500ms
- Supports pagination
- Case-insensitive search

**Examples:**

Search posts:
```bash
curl "http://localhost:8004/search?q=python&type=posts"
```

Search users:
```bash
curl "http://localhost:8004/search?q=john&type=users"
```

Search hashtags:
```bash
curl "http://localhost:8004/search?q=%23python&type=hashtags"
```

Search all types:
```bash
curl "http://localhost:8004/search?q=programming"
```

With pagination:
```bash
curl "http://localhost:8004/search?q=test&type=posts&page=2&page_size=10"
```

**Response Format:**
```json
{
  "type": "posts",
  "results": [
    {
      "id": "post1",
      "user_id": "user1",
      "content": "Post content...",
      "hashtags": ["python", "coding"],
      "likes_count": 10,
      "comments_count": 5,
      "created_at": "2024-01-01T00:00:00Z"
    }
  ],
  "total": 42,
  "page": 1,
  "page_size": 20,
  "has_more": true
}
```

**Error Responses:**

Query too short (422):
```json
{
  "detail": [
    {
      "type": "string_too_short",
      "msg": "String should have at least 2 characters"
    }
  ]
}
```

Invalid type (400):
```json
{
  "error": {
    "code": "INVALID_TYPE",
    "message": "Type must be one of: posts, users, hashtags"
  }
}
```

## Elasticsearch Indices

### Posts Index
- **Name**: `posts`
- **Fields**: id, user_id, content, hashtags, media_urls, likes_count, comments_count, shares_count, created_at, updated_at
- **Analyzer**: Standard analyzer for full-text search

### Users Index
- **Name**: `users`
- **Fields**: id, email, name, bio, profile_picture_url, followers_count, following_count, created_at
- **Analyzer**: Standard analyzer for name and bio

### Hashtags Index
- **Name**: `hashtags`
- **Fields**: tag, posts_count, trending, last_used

## Configuration

Environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| ELASTICSEARCH_HOST | Elasticsearch host | localhost |
| ELASTICSEARCH_PORT | Elasticsearch port | 9200 |
| PORT | Service port | 8004 |

## Development

### Code Style

Follow PEP 8 guidelines for Python code.

### Logging

The service uses Python's built-in logging module with structured logging format.

## Requirements

See `requirements.txt` for all dependencies:
- fastapi==0.104.1
- uvicorn[standard]==0.24.0
- elasticsearch==8.11.0
- pydantic==2.5.0
- pydantic-settings==2.1.0
- python-dotenv==1.0.0

## License

Part of Rede Social Brasileira project.
