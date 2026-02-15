const express = require('express');
const multer = require('multer');
const { getDB } = require('../config/mongodb');
const { uploadImageToS3 } = require('../config/s3');
const config = require('../config');
const { authenticateHTTP } = require('../middleware/auth');
const logger = require('../utils/logger');

const router = express.Router();

// Multer config for in-memory storage (for S3 upload)
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: config.maxImageSizeBytes },
  fileFilter: (req, file, cb) => {
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
    if (allowedTypes.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(new Error('Formato de imagem inválido. Use JPEG, PNG ou WebP.'));
    }
  }
});

/**
 * GET /api/v1/messages
 * Buscar histórico de conversa entre dois usuários
 * Query: otherUserId, page (default 1), limit (default 50)
 */
router.get('/', authenticateHTTP, async (req, res) => {
  try {
    const userId = req.user.userId;
    const otherUserId = req.query.otherUserId;
    const page = parseInt(req.query.page || '1', 10);
    const limit = Math.min(parseInt(req.query.limit || '50', 10), 50);

    if (!otherUserId) {
      return res.status(400).json({
        error: {
          code: 'INVALID_DATA',
          message: 'otherUserId é obrigatório'
        }
      });
    }

    const db = getDB();
    const messagesCollection = db.collection('messages');
    const skip = (page - 1) * limit;

    // Buscar mensagens onde o usuário é sender ou receiver com otherUserId
    const messages = await messagesCollection
      .find({
        $or: [
          { senderId: userId, receiverId: otherUserId },
          { senderId: otherUserId, receiverId: userId }
        ]
      })
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(limit)
      .toArray();

    const total = await messagesCollection.countDocuments({
      $or: [
        { senderId: userId, receiverId: otherUserId },
        { senderId: otherUserId, receiverId: userId }
      ]
    });

    const formatted = messages.map(m => ({
      id: m._id.toString(),
      senderId: m.senderId,
      receiverId: m.receiverId,
      content: m.content,
      mediaUrl: m.mediaUrl,
      isRead: m.isRead,
      readAt: m.readAt?.toISOString() || null,
      deliveredAt: m.deliveredAt?.toISOString() || null,
      createdAt: m.createdAt.toISOString()
    }));

    res.json({
      messages: formatted.reverse(),
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit)
      }
    });
  } catch (error) {
    logger.error({ message: 'Error fetching conversation history', error: error.message });
    res.status(500).json({
      error: {
        code: 'INTERNAL_ERROR',
        message: error.message
      }
    });
  }
});

/**
 * POST /api/v1/messages/upload-image
 * Upload de imagem para mensagem (S3)
 * Retorna URL para enviar via WebSocket
 */
router.post('/upload-image', authenticateHTTP, upload.single('image'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({
        error: {
          code: 'INVALID_DATA',
          message: 'Imagem é obrigatória'
        }
      });
    }

    const userId = req.user.userId;
    const { buffer, mimetype, originalname } = req.file;

    const url = await uploadImageToS3(buffer, mimetype, userId, originalname || 'image.jpg');

    logger.info({ message: 'Image uploaded for message', userId, url });

    res.json({
      success: true,
      mediaUrl: url
    });
  } catch (error) {
    logger.error({ message: 'Error uploading image', error: error.message });
    if (error.message.includes('Formato')) {
      return res.status(400).json({
        error: { code: 'INVALID_FORMAT', message: error.message }
      });
    }
    if (error.code === 'LIMIT_FILE_SIZE') {
      return res.status(400).json({
        error: { code: 'FILE_TOO_LARGE', message: 'Arquivo excede 10MB' }
      });
    }
    res.status(500).json({
      error: {
        code: 'INTERNAL_ERROR',
        message: error.message
      }
    });
  }
});

module.exports = router;
