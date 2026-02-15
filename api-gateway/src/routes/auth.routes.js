const express = require('express');
const router = express.Router();
const authService = require('../services/authService');
const { AppError } = require('../middleware/errorHandler');
const logger = require('../utils/logger');
const { createAuthRateLimiter } = require('../middleware/rateLimiter');

// Inicializar rate limiter para autenticação
let authRateLimiter;
createAuthRateLimiter().then(limiter => {
  authRateLimiter = limiter;
  logger.info({ message: 'Auth rate limiter initialized' });
}).catch(err => {
  logger.error({ message: 'Failed to initialize auth rate limiter', error: err.message });
});

// Middleware para aplicar rate limiter de autenticação
const applyAuthRateLimit = (req, res, next) => {
  if (authRateLimiter) {
    return authRateLimiter(req, res, next);
  }
  next();
};

/**
 * POST /api/v1/auth/register
 * Registrar novo usuário
 * 
 * Body: { email, password, name }
 * Response: { accessToken, refreshToken, expiresIn, tokenType, user }
 */
router.post('/register', applyAuthRateLimit, async (req, res, next) => {
  try {
    const { email, password, name } = req.body;

    // Validação básica
    if (!email || !password || !name) {
      throw new AppError('Email, senha e nome são obrigatórios', 400, 'MISSING_FIELDS');
    }

    // TODO: Chamar User Service para criar usuário
    // Por enquanto, retornamos um mock
    const userId = 'mock-user-id';

    // Gerar tokens
    const tokens = authService.generateTokens(userId, email);

    logger.info({
      message: 'User registered',
      userId,
      email,
      requestId: req.id
    });

    res.status(201).json({
      ...tokens,
      user: {
        userId,
        email,
        name
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * POST /api/v1/auth/login
 * Login de usuário
 * 
 * Body: { email, password }
 * Response: { accessToken, refreshToken, expiresIn, tokenType, user }
 */
router.post('/login', applyAuthRateLimit, async (req, res, next) => {
  try {
    const { email, password } = req.body;

    // Validação básica
    if (!email || !password) {
      throw new AppError('Email e senha são obrigatórios', 400, 'MISSING_CREDENTIALS');
    }

    // TODO: Chamar User Service para validar credenciais
    // Por enquanto, retornamos um mock
    const userId = 'mock-user-id';
    const name = 'Mock User';

    // Gerar tokens
    const tokens = authService.generateTokens(userId, email);

    logger.info({
      message: 'User logged in',
      userId,
      email,
      requestId: req.id
    });

    res.status(200).json({
      ...tokens,
      user: {
        userId,
        email,
        name
      }
    });
  } catch (error) {
    next(error);
  }
});

/**
 * POST /api/v1/auth/refresh
 * Renovar token de acesso usando refresh token
 * 
 * Body: { refreshToken }
 * Response: { accessToken, refreshToken, expiresIn, tokenType }
 */
router.post('/refresh', async (req, res, next) => {
  try {
    const { refreshToken } = req.body;

    if (!refreshToken) {
      throw new AppError('Refresh token é obrigatório', 400, 'MISSING_REFRESH_TOKEN');
    }

    // Verificar refresh token
    const decoded = authService.verifyToken(refreshToken);

    // TODO: Verificar se o refresh token não foi revogado (blacklist no Redis)

    // Gerar novos tokens
    const tokens = authService.generateTokens(decoded.userId, decoded.email);

    logger.info({
      message: 'Token refreshed',
      userId: decoded.userId,
      requestId: req.id
    });

    res.status(200).json(tokens);
  } catch (error) {
    next(error);
  }
});

/**
 * POST /api/v1/auth/logout
 * Logout de usuário (adiciona token à blacklist)
 * 
 * Headers: Authorization: Bearer <token>
 * Response: { message }
 */
router.post('/logout', async (req, res, next) => {
  try {
    const token = authService.extractTokenFromHeader(req.headers.authorization);

    if (!token) {
      throw new AppError('Token não fornecido', 401, 'TOKEN_REQUIRED');
    }

    const decoded = authService.verifyToken(token);

    // TODO: Adicionar token à blacklist no Redis

    logger.info({
      message: 'User logged out',
      userId: decoded.userId,
      requestId: req.id
    });

    res.status(200).json({
      message: 'Logout realizado com sucesso'
    });
  } catch (error) {
    next(error);
  }
});

module.exports = router;
