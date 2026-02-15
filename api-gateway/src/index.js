const app = require('./app');
const config = require('./config');
const logger = require('./utils/logger');

const server = app.listen(config.port, () => {
  logger.info({
    message: 'API Gateway started',
    port: config.port,
    env: config.env,
    nodeVersion: process.version
  });
});

// Graceful shutdown
const gracefulShutdown = (signal) => {
  logger.info({ message: `${signal} received, shutting down gracefully` });
  
  server.close(() => {
    logger.info({ message: 'Server closed' });
    process.exit(0);
  });

  // Force shutdown after 10 seconds
  setTimeout(() => {
    logger.error({ message: 'Forced shutdown after timeout' });
    process.exit(1);
  }, 10000);
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

module.exports = server;
