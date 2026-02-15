const { server } = require('./app');
const config = require('./config');
const logger = require('./utils/logger');
const { connectMongoDB, disconnectMongoDB } = require('./config/mongodb');
const { connectRedis, disconnectRedis } = require('./config/redis');

async function startServer() {
  try {
    // Conectar ao MongoDB
    await connectMongoDB();
    
    // Conectar ao Redis
    await connectRedis();
    
    // Iniciar servidor
    server.listen(config.port, () => {
      logger.info({
        message: 'Messaging Service started',
        port: config.port,
        env: config.env,
        nodeVersion: process.version
      });
    });
  } catch (error) {
    logger.error({
      message: 'Failed to start server',
      error: error.message,
      stack: error.stack
    });
    process.exit(1);
  }
}

// Graceful shutdown
const gracefulShutdown = async (signal) => {
  logger.info({ message: `${signal} received, shutting down gracefully` });
  
  try {
    // Fechar servidor HTTP
    server.close(async () => {
      logger.info({ message: 'HTTP server closed' });
      
      // Desconectar MongoDB
      await disconnectMongoDB();
      
      // Desconectar Redis
      await disconnectRedis();
      
      logger.info({ message: 'All connections closed' });
      process.exit(0);
    });

    // Force shutdown after 10 seconds
    setTimeout(() => {
      logger.error({ message: 'Forced shutdown after timeout' });
      process.exit(1);
    }, 10000);
  } catch (error) {
    logger.error({ message: 'Error during shutdown', error: error.message });
    process.exit(1);
  }
};

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

// Unhandled rejection handler
process.on('unhandledRejection', (reason, promise) => {
  logger.error({
    message: 'Unhandled Rejection',
    reason,
    promise
  });
});

// Unhandled exception handler
process.on('uncaughtException', (error) => {
  logger.error({
    message: 'Uncaught Exception',
    error: error.message,
    stack: error.stack
  });
  process.exit(1);
});

// Iniciar servidor
startServer();
