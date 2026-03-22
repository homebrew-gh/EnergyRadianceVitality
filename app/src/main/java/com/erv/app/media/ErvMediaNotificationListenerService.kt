package com.erv.app.media

import android.service.notification.NotificationListenerService

/**
 * Minimal listener so the system allows [android.media.session.MediaSessionManager.getActiveSessions]
 * for this package. Required for in-app transport controls (play/pause/skip) for the active media session.
 * The user must enable "Notification access" for ERV in system settings; we do not read or store notifications.
 */
class ErvMediaNotificationListenerService : NotificationListenerService()
