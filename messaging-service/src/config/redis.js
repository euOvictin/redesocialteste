const redis = require('redis');
const config = require('./index');
const logger = require('../utils/logger');

let redisClient = null;

async function connectRedis() {
  try {
    if (redisClient && redisClient.isOpen) {
      logger.info({ message: 'Redis already connected' });
      return redisClient;
    }

    logger.info({ message: 'Connecting to Redis...', host: config.redis.host, port: config.redis.port });
    
    redisClient = redis.createClient({
      socket: {
        host: config.redis.host,
        port: config.redis.port
      },
      password: config.redis.password
    });

    redisClient.on('error', (err) => {
      logger.error({ message: 'Redis client error', error: err.message });
    });

    redisClient.on('connect', () => {
      logger.info({ message: 'Redis client connected' });
    });

    redisClient.on('ready', () => {
      logger.info({ message: 'Redis client ready' });
    });

    await redisClient.connect();
    
    // Testar conexão
    await redisClient.ping();
    
    logger.info({ message: 'Redis connected successfully' });
    
    return redisClient;
  } catch (error) {
    logger.error({ message: 'Redis connection failed', error: error.message, stack: error.stack });
    throw error;
  }
}

async function disconnectRedis() {
  try {
    if (redisClient && redisClient.isOpen) {
      await redisClient.quit();
      redisClient = null;
      logger.info({ message: 'Redis disconnected' });
    }
  } catch (error) {
    logger.error({ message: 'Error disconnecting Redis', error: error.message });
    throw error;
  }
}

function getRedisClient() {
  if (!redisClient || !redisClient.isOpen) {
    throw new Error('Redis not connected. Call connectRedis() first.');
  }
  return redisClient;
}

module.exports = {
  connectRedis,
  disconnectRedis,
  getRedisClient
};
