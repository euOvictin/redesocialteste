const logger = require('../utils/logger');

function errorHandler(err, req, res, next) {
  logger.error({
    message: 'Error handler caught exception',
    error: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method,
    requestId: req.id
  });

  // Erro de validação
  if (err.name === 'ValidationError') {
    return res.status(422).json({
      error: {
        code: 'VALIDATION_ERROR',
        message: err.message,
        details: err.details || {},
        requestId: req.id,
        timestamp: new Date().toISOString()
      }
    });
  }

  // Erro de JWT
  if (err.name === 'JsonWebTokenError' || err.name === 'TokenExpiredError') {
    return res.status(401).json({
      error: {
        code: 'INVALID_TOKEN',
        message: 'Token de autenticação inválido ou expirado',
        requestId: req.id,
        timestamp: new Date().toISOString()
      }
    });
  }

  // Erro padrão
  const statusCode = err.statusCode || 500;
  const errorCode = err.code || 'INTERNAL_SERVER_ERROR';
  
  res.status(statusCode).json({
    error: {
      code: errorCode,
      message: err.message || 'Erro interno do servidor',
      requestId: req.id,
      timestamp: new Date().toISOString()
    }
  });
}

module.exports = { errorHandler };
