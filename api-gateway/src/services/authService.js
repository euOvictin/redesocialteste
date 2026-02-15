const jwt = require('jsonwebtoken');
const config = require('../config');
const { AppError } = require('../middleware/errorHandler');

class AuthService {
  /**
   * Gera um token JWT de acesso
   * @param {Object} payload - Dados do usuário (userId, email)
   * @returns {string} Token JWT
   */
  generateAccessToken(payload) {
    return jwt.sign(payload, config.jwt.secret, {
      expiresIn: config.jwt.expiresIn
    });
  }

  /**
   * Gera um token JWT de refresh
   * @param {Object} payload - Dados do usuário (userId)
   * @returns {string} Refresh token JWT
   */
  generateRefreshToken(payload) {
    return jwt.sign(payload, config.jwt.secret, {
      expiresIn: config.jwt.refreshExpiresIn
    });
  }

  /**
   * Gera ambos os tokens (access e refresh)
   * @param {string} userId - ID do usuário
   * @param {string} email - Email do usuário
   * @returns {Object} Objeto com accessToken e refreshToken
   */
  generateTokens(userId, email) {
    const accessToken = this.generateAccessToken({ userId, email });
    const refreshToken = this.generateRefreshToken({ userId });

    return {
      accessToken,
      refreshToken,
      expiresIn: config.jwt.expiresIn,
      tokenType: 'Bearer'
    };
  }

  /**
   * Verifica e decodifica um token JWT
   * @param {string} token - Token JWT
   * @returns {Object} Payload decodificado
   * @throws {AppError} Se o token for inválido ou expirado
   */
  verifyToken(token) {
    try {
      return jwt.verify(token, config.jwt.secret);
    } catch (error) {
      if (error.name === 'TokenExpiredError') {
        throw new AppError('Token expirado', 401, 'TOKEN_EXPIRED');
      }
      if (error.name === 'JsonWebTokenError') {
        throw new AppError('Token inválido', 401, 'INVALID_TOKEN');
      }
      throw new AppError('Erro ao verificar token', 401, 'TOKEN_VERIFICATION_FAILED');
    }
  }

  /**
   * Extrai o token do header Authorization
   * @param {string} authHeader - Header Authorization
   * @returns {string|null} Token extraído ou null
   */
  extractTokenFromHeader(authHeader) {
    if (!authHeader) {
      return null;
    }

    const parts = authHeader.split(' ');
    if (parts.length !== 2 || parts[0] !== 'Bearer') {
      return null;
    }

    return parts[1];
  }
}

module.exports = new AuthService();
