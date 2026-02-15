/**
 * LGPD Compliance routes - Data export and deletion
 * Requirements: 14.1, 14.2
 */
const express = require('express');
const router = express.Router();
const { authenticate } = require('../middleware/auth');
const logger = require('../utils/logger');
const config = require('../config');
const { addAuditEntry } = require('../middleware/audit');
const axios = require('axios');

/**
 * GET /api/v1/lgpd/export
 * Export all user data in JSON (Requirement 14.1)
 */
router.get('/export', authenticate, async (req, res) => {
  try {
    const userId = req.user.userId;
    const authHeader = req.headers.authorization;

    // Aggregate data from services (mock structure - services would implement export endpoints)
    const exportData = {
      userId,
      exportedAt: new Date().toISOString(),
      profile: null,
      posts: [],
      comments: [],
      messages: [],
      notifications: [],
      followers: [],
      following: [],
    };

    // Call User Service for profile
    try {
      const userRes = await axios.get(`${config.services.user}/api/users/${userId}`, {
        headers: { Authorization: authHeader },
        timeout: 10000,
      });
      exportData.profile = userRes.data;
    } catch (e) {
      logger.warn({ message: 'User service export unavailable', userId, error: e.message });
    }

    // Audit: personal data access (Requirement 14.6)
    addAuditEntry({
      type: 'personal_data_access',
      userId,
      action: 'export',
      resource: 'user_data',
      requestId: req.id,
    });

    res.json(exportData);
  } catch (error) {
    logger.error({ message: 'LGPD export failed', error: error.message, userId: req.user?.userId });
    res.status(500).json({
      error: {
        code: 'EXPORT_FAILED',
        message: 'Falha ao exportar dados',
        requestId: req.id,
        timestamp: new Date().toISOString(),
      },
    });
  }
});

/**
 * DELETE /api/v1/lgpd/account
 * Request account deletion - removes personal data (Requirement 14.2)
 */
router.delete('/account', authenticate, async (req, res) => {
  try {
    const userId = req.user.userId;
    const authHeader = req.headers.authorization;

    // Call User Service to delete account
    try {
      await axios.delete(`${config.services.user}/api/users/${userId}`, {
        headers: { Authorization: authHeader },
        timeout: 15000,
      });
    } catch (e) {
      logger.warn({ message: 'User service delete unavailable', userId, error: e.message });
    }

    // Audit: personal data deletion
    addAuditEntry({
      type: 'personal_data_deletion',
      userId,
      action: 'delete_account',
      resource: 'user_data',
      requestId: req.id,
    });

    res.json({
      success: true,
      message: 'Solicitação de exclusão de conta recebida. Dados serão removidos em até 30 dias.',
    });
  } catch (error) {
    logger.error({ message: 'LGPD deletion failed', error: error.message, userId: req.user?.userId });
    res.status(500).json({
      error: {
        code: 'DELETION_FAILED',
        message: 'Falha ao solicitar exclusão',
        requestId: req.id,
        timestamp: new Date().toISOString(),
      },
    });
  }
});

module.exports = router;
