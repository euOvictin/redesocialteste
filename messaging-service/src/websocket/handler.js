const { authenticateWebSocket } = require('../middleware/auth');
const { getRedisClient } = require('../config/redis');
const logger = require('../utils/logger');

/**
 * Configura os handlers de WebSocket
 */
function setupWebSocketHandlers(io) {
  // Middleware de autenticação para Socket.io
  io.use((socket, next) => {
    const token = socket.handshake.auth.token || socket.handshake.query.token;
    
    const user = authenticateWebSocket(token);
    
    if (!user) {
      logger.warn({ message: 'WebSocket authentication failed', socketId: socket.id });
      return next(new Error('Authentication failed'));
    }
    
    socket.user = user;
    logger.info({ message: 'WebSocket authenticated', userId: user.userId, socketId: socket.id });
    next();
  });

  // Handler de conexão
  io.on('connection', async (socket) => {
    const userId = socket.user.userId;
    
    logger.info({ 
      message: 'WebSocket client connected', 
      userId, 
      socketId: socket.id 
    });

    try {
      // Armazenar sessão no Redis
      const redis = getRedisClient();
      await redis.set(`ws:session:${userId}`, socket.id, { EX: 3600 }); // TTL de 1 hora
      
      // Entrar na sala do usuário (para mensagens diretas)
      socket.join(`user:${userId}`);
      
      logger.info({ message: 'User joined room', userId, room: `user:${userId}` });
    } catch (error) {
      logger.error({ message: 'Error storing WebSocket session', error: error.message, userId });
    }

    // Handler para enviar mensagem
    socket.on('send_message', async (data, callback) => {
      try {
        logger.info({ message: 'Received send_message event', userId, data });
        
        // Validação básica
        if (!data.receiverId || !data.content) {
          const error = { code: 'INVALID_DATA', message: 'receiverId e content são obrigatórios' };
          logger.warn({ message: 'Invalid message data', userId, error });
          if (callback) callback({ success: false, error });
          return;
        }

        // Validar comprimento da mensagem (1-5000 caracteres)
        if (typeof data.content !== 'string' || data.content.length < 1 || data.content.length > 5000) {
          const error = { code: 'MESSAGE_TOO_LONG', message: 'Mensagem deve ter entre 1 e 5000 caracteres' };
          logger.warn({ message: 'Invalid message length', userId, contentLength: data.content?.length, error });
          if (callback) callback({ success: false, error });
          return;
        }

        // Validar que não está enviando para si mesmo
        if (data.receiverId === userId) {
          const error = { code: 'INVALID_RECEIVER', message: 'Não é possível enviar mensagem para si mesmo' };
          logger.warn({ message: 'Cannot send message to self', userId, error });
          if (callback) callback({ success: false, error });
          return;
        }

        // Criar objeto de mensagem
        const { getDB } = require('../config/mongodb');
        const db = getDB();
        const messagesCollection = db.collection('messages');
        
        const message = {
          senderId: userId,
          receiverId: data.receiverId,
          content: data.content,
          mediaUrl: data.mediaUrl || null,
          isRead: false,
          deliveredAt: null,
          readAt: null,
          createdAt: new Date()
        };

        // Armazenar mensagem no MongoDB
        const result = await messagesCollection.insertOne(message);
        message._id = result.insertedId;
        
        logger.info({ 
          message: 'Message stored in MongoDB', 
          messageId: message._id.toString(), 
          from: userId, 
          to: data.receiverId 
        });

        // Verificar se destinatário está online no Redis
        const redis = getRedisClient();
        const receiverSocketId = await redis.get(`ws:session:${data.receiverId}`);
        
        if (receiverSocketId) {
          // Destinatário está online - entregar via WebSocket
          logger.info({ 
            message: 'Receiver is online, delivering via WebSocket', 
            receiverId: data.receiverId, 
            socketId: receiverSocketId 
          });
          
          // Atualizar deliveredAt
          message.deliveredAt = new Date();
          await messagesCollection.updateOne(
            { _id: message._id },
            { $set: { deliveredAt: message.deliveredAt } }
          );
          
          // Emitir evento para o destinatário
          io.to(`user:${data.receiverId}`).emit('message_received', {
            messageId: message._id.toString(),
            senderId: message.senderId,
            receiverId: message.receiverId,
            content: message.content,
            mediaUrl: message.mediaUrl,
            createdAt: message.createdAt.toISOString(),
            deliveredAt: message.deliveredAt.toISOString()
          });
          
          logger.info({ 
            message: 'Message delivered to receiver', 
            messageId: message._id.toString() 
          });
        } else {
          // Destinatário está offline - mensagem já foi armazenada
          logger.info({ 
            message: 'Receiver is offline, message stored for later delivery', 
            receiverId: data.receiverId,
            messageId: message._id.toString()
          });
        }

        // Enviar confirmação de entrega ao remetente
        const response = {
          success: true,
          messageId: message._id.toString(),
          createdAt: message.createdAt.toISOString(),
          deliveredAt: message.deliveredAt ? message.deliveredAt.toISOString() : null,
          status: receiverSocketId ? 'delivered' : 'stored'
        };
        
        if (callback) {
          callback(response);
        }
        
        // Emitir evento de confirmação de entrega ao remetente
        socket.emit('message_delivered', {
          messageId: message._id.toString(),
          status: receiverSocketId ? 'delivered' : 'stored',
          deliveredAt: message.deliveredAt ? message.deliveredAt.toISOString() : null
        });
        
        logger.info({ 
          message: 'Delivery confirmation sent to sender', 
          messageId: message._id.toString(),
          status: response.status
        });
        
      } catch (error) {
        logger.error({ message: 'Error handling send_message', error: error.message, stack: error.stack, userId });
        if (callback) {
          callback({ success: false, error: { code: 'INTERNAL_ERROR', message: error.message } });
        }
      }
    });

    // Handler para marcar mensagem como lida
    socket.on('mark_as_read', async (data, callback) => {
      try {
        logger.info({ message: 'Received mark_as_read event', userId, data });
        
        // Validação básica
        if (!data.messageId) {
          const error = { code: 'INVALID_DATA', message: 'messageId é obrigatório' };
          logger.warn({ message: 'Invalid mark_as_read data', userId, error });
          if (callback) callback({ success: false, error });
          return;
        }

        const { getDB } = require('../config/mongodb');
        const { ObjectId } = require('mongodb');
        const db = getDB();
        const messagesCollection = db.collection('messages');

        let message;
        try {
          message = await messagesCollection.findOne({ _id: new ObjectId(data.messageId) });
        } catch (e) {
          const error = { code: 'INVALID_DATA', message: 'messageId inválido' };
          if (callback) callback({ success: false, error });
          return;
        }

        if (!message) {
          const error = { code: 'NOT_FOUND', message: 'Mensagem não encontrada' };
          if (callback) callback({ success: false, error });
          return;
        }

        if (message.receiverId !== userId) {
          const error = { code: 'FORBIDDEN', message: 'Apenas o destinatário pode marcar como lida' };
          if (callback) callback({ success: false, error });
          return;
        }

        if (message.isRead) {
          if (callback) callback({ success: true, alreadyRead: true });
          return;
        }

        const readAt = new Date();
        await messagesCollection.updateOne(
          { _id: message._id },
          { $set: { isRead: true, readAt } }
        );

        logger.info({ message: 'Message marked as read', messageId: data.messageId, userId });

        // Enviar confirmação de leitura ao remetente via WebSocket
        io.to(`user:${message.senderId}`).emit('message_read', {
          messageId: message._id.toString(),
          readBy: userId,
          readAt: readAt.toISOString()
        });

        if (callback) {
          callback({ success: true, readAt: readAt.toISOString() });
        }
      } catch (error) {
        logger.error({ message: 'Error handling mark_as_read', error: error.message, userId });
        if (callback) {
          callback({ success: false, error: { code: 'INTERNAL_ERROR', message: error.message } });
        }
      }
    });

    // Handler de desconexão
    socket.on('disconnect', async (reason) => {
      logger.info({ 
        message: 'WebSocket client disconnected', 
        userId, 
        socketId: socket.id, 
        reason 
      });

      try {
        // Remover sessão do Redis
        const redis = getRedisClient();
        await redis.del(`ws:session:${userId}`);
      } catch (error) {
        logger.error({ message: 'Error removing WebSocket session', error: error.message, userId });
      }
    });

    // Handler de erro
    socket.on('error', (error) => {
      logger.error({ 
        message: 'WebSocket error', 
        userId, 
        socketId: socket.id, 
        error: error.message 
      });
    });
  });

  logger.info({ message: 'WebSocket handlers configured' });
}

module.exports = { setupWebSocketHandlers };
