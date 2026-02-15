const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const crypto = require('crypto');
const config = require('./config');
const logger = require('./utils/logger');
const { errorHandler } = require('./middleware/errorHandler');
const { createGeneralRateLimiter } = require('./middleware/rateLimiter');
const { sanitizationMiddleware } = require('./middleware/sanitization');
const { auditMiddleware } = require('./middleware/audit');

const app = express();

// Inicializar rate limiter geral (será aplicado após autenticação)
let generalRateLimiter;
createGeneralRateLimiter().then(limiter => {
  generalRateLimiter = limiter;
  logger.info({ message: 'General rate limiter initialized' });
}).catch(err => {
  logger.error({ message: 'Failed to initialize rate limiter', error: err.message });
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

// Sanitization - prevent XSS, SQL injection, code injection (Requirement 10.4)
app.use(sanitizationMiddleware);

// Audit - log suspicious access (Requirements 10.6, 10.7)
app.use(auditMiddleware);

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

// Rate limiting geral (aplicado após autenticação em rotas protegidas)
app.use((req, res, next) => {
  if (generalRateLimiter && req.path !== '/health' && req.path !== '/ready') {
    return generalRateLimiter(req, res, next);
  }
  next();
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    service: 'api-gateway',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// Readiness check endpoint
app.get('/ready', (req, res) => {
  // Aqui podemos adicionar verificações de dependências (Redis, etc)
  res.status(200).json({
    status: 'ready',
    service: 'api-gateway',
    timestamp: new Date().toISOString()
  });
});

// Swagger UI - OpenAPI 3.0 documentation (Requirement 15.1)
const { swaggerUi, specs } = require('./swagger');
app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(specs));

// API version endpoint
app.get('/api/v1', (req, res) => {
  res.status(200).json({
    version: '1.0.0',
    service: 'api-gateway',
    endpoints: {
      auth: '/api/v1/auth',
      users: '/api/v1/users',
      posts: '/api/v1/posts',
      feed: '/api/v1/feed',
      stories: '/api/v1/stories',
      messages: '/api/v1/messages',
      search: '/api/v1/search',
      notifications: '/api/v1/notifications'
    }
  });
});

// Rotas da API
const authRoutes = require('./routes/auth.routes');
const lgpdRoutes = require('./routes/lgpd.routes');

app.use('/api/v1/auth', authRoutes);
app.use('/api/v1/lgpd', lgpdRoutes);

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

module.exports = app;
