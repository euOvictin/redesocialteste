const jwt = require('jsonwebtoken');
const config = require('../config');
const logger = require('../utils/logger');

/**
 * Middleware para autenticar requisições HTTP via JWT
 */
function authenticateHTTP(req, res, next) {
  try {
    const authHeader = req.headers.authorization;
    
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        error: {
          code: 'UNAUTHORIZED',
          message: 'Token de autenticação não fornecido',
          timestamp: new Date().toISOString()
        }
      });
    }

    const token = authHeader.substring(7);
    
    try {
      const decoded = jwt.verify(token, config.jwt.secret);
      req.user = {
        userId: decoded.userId,
        email: decoded.email
      };
      next();
    } catch (jwtError) {
      logger.warn({ message: 'Invalid JWT token', error: jwtError.message });
      return res.status(401).json({
        error: {
          code: 'INVALID_TOKEN',
          message: 'Token de autenticação inválido ou expirado',
          timestamp: new Date().toISOString()
        }
      });
    }
  } catch (error) {
    logger.error({ message: 'Authentication error', error: error.message });
    return res.status(500).json({
      error: {
        code: 'INTERNAL_SERVER_ERROR',
        message: 'Erro ao processar autenticação',
        timestamp: new Date().toISOString()
      }
    });
  }
}

/**
 * Função para autenticar conexões WebSocket via JWT
 * Retorna o payload decodificado ou null se inválido
 */
function authenticateWebSocket(token) {
  try {
    if (!token) {
      return null;
    }

    const decoded = jwt.verify(token, config.jwt.secret);
    return {
      userId: decoded.userId,
      email: decoded.email
    };
  } catch (error) {
    logger.warn({ message: 'WebSocket authentication failed', error: error.message });
    return null;
  }
}

module.exports = {
  authenticateHTTP,
  authenticateWebSocket
};
