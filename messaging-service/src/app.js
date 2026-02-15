const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const helmet = require('helmet');
const crypto = require('crypto');
const config = require('./config');
const logger = require('./utils/logger');
const { errorHandler } = require('./middleware/errorHandler');
const { setupWebSocketHandlers } = require('./websocket/handler');

const app = express();
const server = http.createServer(app);

// Configurar Socket.io
const io = new Server(server, {
  cors: config.websocket.cors,
  pingTimeout: config.websocket.pingTimeout,
  pingInterval: config.websocket.pingInterval
});

// Middleware de segurança
app.use(helmet());

// CORS
app.use(cors(config.cors));

// Body parser
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Request ID para rastreamento
app.use((req, res, next) => {
  req.id = crypto.randomUUID();
  res.setHeader('X-Request-ID', req.id);
  next();
});

// Logging de requisições
app.use((req, res, next) => {
  logger.info({
    message: 'Incoming request',
    method: req.method,
    path: req.path,
    requestId: req.id,
    ip: req.ip
  });
  next();
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    service: 'messaging-service',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// Readiness check endpoint
app.get('/ready', async (req, res) => {
  try {
    const { getDB } = require('./config/mongodb');
    const { getRedisClient } = require('./config/redis');
    
    // Verificar MongoDB
    const db = getDB();
    await db.command({ ping: 1 });
    
    // Verificar Redis
    const redis = getRedisClient();
    await redis.ping();
    
    res.status(200).json({
      status: 'ready',
      service: 'messaging-service',
      timestamp: new Date().toISOString(),
      dependencies: {
        mongodb: 'connected',
        redis: 'connected'
      }
    });
  } catch (error) {
    logger.error({ message: 'Readiness check failed', error: error.message });
    res.status(503).json({
      status: 'not_ready',
      service: 'messaging-service',
      timestamp: new Date().toISOString(),
      error: error.message
    });
  }
});

// API version endpoint
app.get('/api/v1', (req, res) => {
  res.status(200).json({
    version: '1.0.0',
    service: 'messaging-service',
    endpoints: {
      messages: '/api/v1/messages',
      conversations: '/api/v1/conversations'
    },
    websocket: {
      path: '/socket.io',
      events: {
        send_message: 'Enviar mensagem',
        mark_as_read: 'Marcar mensagem como lida',
        message_received: 'Mensagem recebida (evento do servidor)',
        message_delivered: 'Mensagem entregue (evento do servidor)',
        message_read: 'Mensagem lida (evento do servidor)'
      }
    }
  });
});

// Rotas da API
const messageRoutes = require('./routes/message.routes');
const conversationRoutes = require('./routes/conversation.routes');
app.use('/api/v1/messages', messageRoutes);
app.use('/api/v1/conversations', conversationRoutes);

// 404 handler
app.use((req, res) => {
  res.status(404).json({
    error: {
      code: 'NOT_FOUND',
      message: 'Endpoint não encontrado',
      path: req.path,
      requestId: req.id,
      timestamp: new Date().toISOString()
    }
  });
});

// Error handler
app.use(errorHandler);

// Configurar handlers de WebSocket
setupWebSocketHandlers(io);

// Exportar app, server e io para testes
module.exports = { app, server, io };
