from fastapi import APIRouter, HTTPException, Query
from typing import Optional
from src.models import FeedResponse, RelevanceScoreRequest, RelevanceScoreResponse
from src.services.recommendation_service import RecommendationService

router = APIRouter()
recommendation_service = RecommendationService()


@router.get("/feed/{user_id}", response_model=FeedResponse)
async def get_feed(
    user_id: str,
    cursor: Optional[str] = Query(None, description="Pagination cursor"),
    limit: int = Query(20, ge=1, le=100, description="Number of posts per page")
):
    """
    Get personalized feed for a user.
    
    Returns posts from followed users ordered by relevance score.
    """
    try:
        return await recommendation_service.generate_feed(user_id, cursor, limit)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/score", response_model=RelevanceScoreResponse)
async def calculate_score(request: RelevanceScoreRequest):
    """
    Calculate relevance score for a specific post and user.
    """
    try:
        score = await recommendation_service.calculate_relevance_score(
            request.user_id,
            request.post_id
        )
        return RelevanceScoreResponse(
            post_id=request.post_id,
            relevance_score=score
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/trending")
async def get_trending_posts(limit: int = Query(20, ge=1, le=100)):
    """
    Get trending posts for users without followers.
    
    Returns popular posts based on global relevance scores.
    """
    try:
        return await recommendation_service.get_trending_posts(limit)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/invalidate-cache/{user_id}")
async def invalidate_user_cache(user_id: str):
    """
    Invalidate feed cache for a specific user.
    """
    try:
        await recommendation_service.invalidate_user_cache(user_id)
        return {"status": "success", "message": f"Cache invalidated for user {user_id}"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
