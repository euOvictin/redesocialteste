const express = require('express');
const { getDB } = require('../config/mongodb');
const { authenticateHTTP } = require('../middleware/auth');
const logger = require('../utils/logger');

const router = express.Router();

/**
 * GET /api/v1/conversations
 * Listar conversas do usuário com última mensagem e contador de não lidas
 */
router.get('/', authenticateHTTP, async (req, res) => {
  try {
    const userId = req.user.userId;

    const db = getDB();
    const messagesCollection = db.collection('messages');

    const pipeline = [
      {
        $match: {
          $or: [{ senderId: userId }, { receiverId: userId }]
        }
      },
      {
        $sort: { createdAt: -1 }
      },
      {
        $group: {
          _id: {
            $cond: [
              { $eq: ['$senderId', userId] },
              '$receiverId',
              '$senderId'
            ]
          },
          lastMessage: { $first: '$$ROOT' },
          unreadCount: {
            $sum: {
              $cond: [
                {
                  $and: [
                    { $eq: ['$receiverId', userId] },
                    { $eq: ['$isRead', false] }
                  ]
                },
                1,
                0
              ]
            }
          }
        }
      },
      {
        $project: {
          otherUserId: '$_id',
          lastMessage: {
            id: { $toString: '$lastMessage._id' },
            senderId: '$lastMessage.senderId',
            receiverId: '$lastMessage.receiverId',
            content: '$lastMessage.content',
            mediaUrl: '$lastMessage.mediaUrl',
            isRead: '$lastMessage.isRead',
            createdAt: '$lastMessage.createdAt'
          },
          unreadCount: 1,
          _id: 0
        }
      },
      { $sort: { 'lastMessage.createdAt': -1 } }
    ];

    const conversations = await messagesCollection.aggregate(pipeline).toArray();

    const formatted = conversations.map(c => ({
      otherUserId: c.otherUserId,
      lastMessage: c.lastMessage ? {
        ...c.lastMessage,
        createdAt: c.lastMessage.createdAt?.toISOString?.() || c.lastMessage.createdAt
      } : null,
      unreadCount: c.unreadCount || 0
    }));

    res.json({ conversations: formatted });
  } catch (error) {
    logger.error({ message: 'Error fetching conversations', error: error.message });
    res.status(500).json({
      error: {
        code: 'INTERNAL_ERROR',
        message: error.message
      }
    });
  }
});

module.exports = router;
