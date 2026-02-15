"""Push notification service - FCM and APNs (with mock fallback)."""
import logging
from typing import Optional

from src.database import get_db
from src.config import settings

logger = logging.getLogger(__name__)


class PushService:
    """Send push notifications via FCM (Android) and APNs (iOS)."""
    
    def __init__(self):
        self._fcm_initialized = False
        self._apns_initialized = False
    
    async def _is_push_enabled(self, user_id: str) -> bool:
        """Check if user has push notifications enabled."""
        db = get_db()
        prefs = await db.notification_preferences.find_one({"user_id": user_id})
        return (prefs or {}).get("push_enabled", True)
    
    async def send_push(
        self,
        user_id: str,
        title: str,
        body: str,
        data: Optional[dict] = None,
        fcm_token: Optional[str] = None,
        apns_token: Optional[str] = None,
    ) -> bool:
        """
        Send push notification to user.
        In production, would use FCM for Android and APNs for iOS.
        Returns True if push was sent (or mock succeeded).
        """
        if not await self._is_push_enabled(user_id):
            logger.debug(f"Push disabled for user {user_id}, skipping")
            return False
        
        data = data or {}
        
        # Try FCM if token available
        if fcm_token and settings.fcm_server_key:
            try:
                return await self._send_fcm(fcm_token, title, body, data)
            except Exception as e:
                logger.error(f"FCM send failed: {e}")
        
        # Try APNs if token available
        if apns_token and settings.apns_key_id:
            try:
                return await self._send_apns(apns_token, title, body, data)
            except Exception as e:
                logger.error(f"APNs send failed: {e}")
        
        # Mock: log and return True for testing
        if not fcm_token and not apns_token:
            logger.info(f"[MOCK] Push notification for user {user_id}: {title} - {body}")
            return True
        
        return False
    
    async def _send_fcm(self, token: str, title: str, body: str, data: dict) -> bool:
        """Send via Firebase Cloud Messaging."""
        try:
            try:
                import firebase_admin
                from firebase_admin import messaging
            except ImportError:
                logger.warning("firebase-admin not installed, using mock")
                return True
            
            if not firebase_admin._apps:
                firebase_admin.initialize_app()
            
            message = messaging.Message(
                notification=messaging.Notification(title=title, body=body),
                data={k: str(v) for k, v in data.items()},
                token=token,
            )
            messaging.send(message)
            logger.info("FCM push sent successfully")
            return True
        except ImportError:
            logger.warning("firebase-admin not available, using mock")
            return True
        except Exception as e:
            logger.error(f"FCM error: {e}")
            raise
    
    async def _send_apns(self, token: str, title: str, body: str, data: dict) -> bool:
        """Send via Apple Push Notification service."""
        try:
            from apns2.client import APNsClient
            from apns2.payload import Payload
            
            if not settings.apns_key_file:
                logger.warning("APNs key file not configured, using mock")
                return True
            
            client = APNsClient(
                settings.apns_key_file,
                use_sandbox=True,
            )
            payload = Payload(alert=body, sound="default", badge=1, custom=data)
            client.send_notification(token, payload, settings.apns_bundle_id)
            logger.info("APNs push sent successfully")
            return True
        except ImportError:
            logger.warning("apns2 not available, using mock")
            return True
        except Exception as e:
            logger.error(f"APNs error: {e}")
            raise
