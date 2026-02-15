const authService = require('../services/authService');
const { AppError } = require('./errorHandler');
const logger = require('../utils/logger');

/**
 * Middleware de autenticação JWT
 * Verifica se o token JWT é válido e adiciona os dados do usuário ao req.user
 */
const authenticate = (req, res, next) => {
  try {
    // Extrair token do header
    const token = authService.extractTokenFromHeader(req.headers.authorization);

    if (!token) {
      throw new AppError('Token não fornecido', 401, 'TOKEN_REQUIRED');
    }

    // Verificar e decodificar token
    const decoded = authService.verifyToken(token);

    // Adicionar dados do usuário à requisição
    req.user = {
      userId: decoded.userId,
      email: decoded.email
    };

    logger.debug({
      message: 'User authenticated',
      userId: decoded.userId,
      requestId: req.id
    });

    next();
  } catch (error) {
    next(error);
  }
};

/**
 * Middleware opcional de autenticação
 * Adiciona dados do usuário se o token for válido, mas não bloqueia se não houver token
 */
const optionalAuth = (req, res, next) => {
  try {
    const token = authService.extractTokenFromHeader(req.headers.authorization);

    if (token) {
      const decoded = authService.verifyToken(token);
      req.user = {
        userId: decoded.userId,
        email: decoded.email
      };
    }

    next();
  } catch (error) {
    // Ignora erros de autenticação no modo opcional
    next();
  }
};

module.exports = { authenticate, optionalAuth };
