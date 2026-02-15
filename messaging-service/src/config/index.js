require('dotenv').config();

const config = {
  port: process.env.MESSAGING_SERVICE_PORT || 8006,
  env: process.env.NODE_ENV || 'development',
  
  jwt: {
    secret: process.env.JWT_SECRET || 'your-secret-key-change-in-production'
  },
  
  mongodb: {
    uri: process.env.MONGODB_URI || 'mongodb://admin:admin@localhost:27017/rede_social?authSource=admin',
    dbName: process.env.MONGODB_DB_NAME || 'rede_social',
    options: {
      maxPoolSize: 10,
      minPoolSize: 2,
      serverSelectionTimeoutMS: 5000,
      socketTimeoutMS: 45000
    }
  },
  
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD || 'redis123'
  },
  
  cors: {
    origin: process.env.CORS_ORIGIN || '*',
    credentials: true
  },
  
  websocket: {
    pingTimeout: parseInt(process.env.WS_PING_TIMEOUT || '60000', 10),
    pingInterval: parseInt(process.env.WS_PING_INTERVAL || '25000', 10),
    cors: {
      origin: process.env.CORS_ORIGIN || '*',
      credentials: true
    }
  },

  s3: {
    endpoint: process.env.S3_ENDPOINT || 'http://localhost:9000',
    accessKey: process.env.S3_ACCESS_KEY || 'minioadmin',
    secretKey: process.env.S3_SECRET_KEY || 'minioadmin',
    bucket: process.env.S3_BUCKET_NAME || 'rede-social-media',
    region: process.env.S3_REGION || 'us-east-1'
  },

  maxImageSizeBytes: parseInt(process.env.MAX_IMAGE_SIZE_MB || '10', 10) * 1024 * 1024
};

module.exports = config;
