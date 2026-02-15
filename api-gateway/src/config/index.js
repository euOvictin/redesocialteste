require('dotenv').config();

module.exports = {
  port: process.env.API_GATEWAY_PORT || 3000,
  env: process.env.NODE_ENV || 'development',
  
  jwt: {
    secret: process.env.JWT_SECRET || 'your-secret-key-change-in-production',
    expiresIn: process.env.JWT_EXPIRES_IN || '24h',
    refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '7d'
  },
  
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379'),
    password: process.env.REDIS_PASSWORD || undefined
  },
  
  rateLimit: {
    general: {
      windowMs: 60 * 1000, // 1 minuto
      max: 100 // 100 requisições por minuto
    },
    auth: {
      windowMs: 60 * 1000, // 1 minuto
      max: 10 // 10 requisições por minuto
    }
  },
  
  services: {
    user: process.env.USER_SERVICE_URL || 'http://localhost:3001',
    content: process.env.CONTENT_SERVICE_URL || 'http://localhost:3002',
    messaging: process.env.MESSAGING_SERVICE_URL || 'http://localhost:3003',
    search: process.env.SEARCH_SERVICE_URL || 'http://localhost:3004',
    notification: process.env.NOTIFICATION_SERVICE_URL || 'http://localhost:3005',
    recommendation: process.env.RECOMMENDATION_SERVICE_URL || 'http://localhost:3006'
  },
  
  circuitBreaker: {
    timeout: 30000, // 30 segundos
    errorThresholdPercentage: 50,
    resetTimeout: 30000 // 30 segundos
  },
  
  cors: {
    origin: process.env.CORS_ORIGIN || '*',
    credentials: true
  }
};
