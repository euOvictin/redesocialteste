const rateLimit = require('express-rate-limit');
const RedisStore = require('rate-limit-redis');
const redis = require('redis');
const config = require('../config');
const logger = require('../utils/logger');

// Criar cliente Redis
let redisClient;
let useMemoryStore = false;

const initRedisClient = async () => {
  if (!redisClient && !useMemoryStore) {
    try {
      redisClient = redis.createClient({
        socket: {
          host: config.redis.host,
          port: config.redis.port,
          connectTimeout: 2000,
          reconnectStrategy: () => {
            // Não tentar reconectar, usar memory store
            useMemoryStore = true;
            return false;
          }
        },
        password: config.redis.password
      });

      redisClient.on('error', (err) => {
        logger.warn({ message: 'Redis unavailable, using memory store', error: err.message });
        useMemoryStore = true;
      });

      redisClient.on('connect', () => {
        logger.info({ message: 'Redis Client Connected for Rate Limiting' });
      });

      await Promise.race([
        redisClient.connect(),
        new Promise((_, reject) => setTimeout(() => reject(new Error('Redis connection timeout')), 2000))
      ]);
    } catch (err) {
      logger.warn({ message: 'Redis unavailable, using memory store for rate limiting', error: err.message });
      useMemoryStore = true;
      redisClient = null;
    }
  }
  return redisClient;
};

// Rate limiter geral (100 req/min por usuário autenticado)
const createGeneralRateLimiter = async () => {
  await initRedisClient();
  
  const limiterConfig = {
    windowMs: config.rateLimit.general.windowMs,
    max: config.rateLimit.general.max,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => {
      // Usar userId se autenticado, senão usar IP
      return req.user?.userId || req.ip;
    },
    handler: (req, res) => {
      const retryAfter = Math.ceil(config.rateLimit.general.windowMs / 1000);
      
      logger.warn({
        message: 'Rate limit exceeded',
        type: 'general',
        userId: req.user?.userId,
        ip: req.ip,
        path: req.path,
        requestId: req.id
      });

      res.status(429).json({
        error: {
          code: 'TOO_MANY_REQUESTS',
          message: 'Limite de requisições excedido. Tente novamente mais tarde.',
          retryAfter: retryAfter,
          requestId: req.id,
          timestamp: new Date().toISOString()
        }
      });
    },
    skip: (req) => {
      // Não aplicar rate limit em health checks
      return req.path === '/health' || req.path === '/ready';
    }
  };

  // Usar Redis store se disponível, senão usar memory store
  if (redisClient && !useMemoryStore) {
    limiterConfig.store = new RedisStore({
      sendCommand: (...args) => redisClient.sendCommand(args),
      prefix: 'rl:general:'
    });
  }
  
  return rateLimit(limiterConfig);
};

// Rate limiter para autenticação (10 req/min)
const createAuthRateLimiter = async () => {
  await initRedisClient();
  
  const limiterConfig = {
    windowMs: config.rateLimit.auth.windowMs,
    max: config.rateLimit.auth.max,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => {
      // Para endpoints de auth, usar IP + email (se fornecido)
      const email = req.body?.email || '';
      return `${req.ip}:${email}`;
    },
    handler: (req, res) => {
      const retryAfter = Math.ceil(config.rateLimit.auth.windowMs / 1000);
      
      logger.warn({
        message: 'Auth rate limit exceeded',
        type: 'auth',
        ip: req.ip,
        email: req.body?.email,
        path: req.path,
        requestId: req.id
      });

      res.status(429).json({
        error: {
          code: 'TOO_MANY_REQUESTS',
          message: 'Muitas tentativas de autenticação. Tente novamente mais tarde.',
          retryAfter: retryAfter,
          requestId: req.id,
          timestamp: new Date().toISOString()
        }
      });
    }
  };

  // Usar Redis store se disponível, senão usar memory store
  if (redisClient && !useMemoryStore) {
    limiterConfig.store = new RedisStore({
      sendCommand: (...args) => redisClient.sendCommand(args),
      prefix: 'rl:auth:'
    });
  }
  
  return rateLimit(limiterConfig);
};

// Função para fechar conexão Redis (útil para testes)
const closeRedisClient = async () => {
  if (redisClient) {
    try {
      await redisClient.quit();
    } catch (err) {
      logger.warn({ message: 'Error closing Redis client', error: err.message });
    }
    redisClient = null;
  }
  useMemoryStore = false;
};

module.exports = {
  createGeneralRateLimiter,
  createAuthRateLimiter,
  closeRedisClient
};
