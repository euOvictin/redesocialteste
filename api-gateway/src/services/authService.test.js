const authService = require('./authService');
const jwt = require('jsonwebtoken');
const config = require('../config');

describe('AuthService', () => {
  describe('generateAccessToken', () => {
    it('should generate a valid access token', () => {
      const payload = { userId: 'user-123', email: 'test@example.com' };
      const token = authService.generateAccessToken(payload);

      expect(token).toBeDefined();
      expect(typeof token).toBe('string');

      const decoded = jwt.verify(token, config.jwt.secret);
      expect(decoded.userId).toBe(payload.userId);
      expect(decoded.email).toBe(payload.email);
    });
  });

  describe('generateRefreshToken', () => {
    it('should generate a valid refresh token', () => {
      const payload = { userId: 'user-123' };
      const token = authService.generateRefreshToken(payload);

      expect(token).toBeDefined();
      expect(typeof token).toBe('string');

      const decoded = jwt.verify(token, config.jwt.secret);
      expect(decoded.userId).toBe(payload.userId);
    });
  });

  describe('generateTokens', () => {
    it('should generate both access and refresh tokens', () => {
      const userId = 'user-123';
      const email = 'test@example.com';

      const result = authService.generateTokens(userId, email);

      expect(result).toHaveProperty('accessToken');
      expect(result).toHaveProperty('refreshToken');
      expect(result).toHaveProperty('expiresIn');
      expect(result).toHaveProperty('tokenType', 'Bearer');

      // Verificar access token
      const decodedAccess = jwt.verify(result.accessToken, config.jwt.secret);
      expect(decodedAccess.userId).toBe(userId);
      expect(decodedAccess.email).toBe(email);

      // Verificar refresh token
      const decodedRefresh = jwt.verify(result.refreshToken, config.jwt.secret);
      expect(decodedRefresh.userId).toBe(userId);
    });
  });

  describe('verifyToken', () => {
    it('should verify and decode a valid token', () => {
      const payload = { userId: 'user-123', email: 'test@example.com' };
      const token = authService.generateAccessToken(payload);

      const decoded = authService.verifyToken(token);

      expect(decoded.userId).toBe(payload.userId);
      expect(decoded.email).toBe(payload.email);
    });

    it('should throw TOKEN_EXPIRED error for expired token', () => {
      const token = jwt.sign(
        { userId: 'user-123' },
        config.jwt.secret,
        { expiresIn: '0s' }
      );

      // Aguardar 1 segundo para garantir expiração
      return new Promise((resolve) => {
        setTimeout(() => {
          expect(() => authService.verifyToken(token)).toThrow('Token expirado');
          resolve();
        }, 1000);
      });
    });

    it('should throw INVALID_TOKEN error for malformed token', () => {
      const invalidToken = 'invalid.token.here';

      expect(() => authService.verifyToken(invalidToken)).toThrow('Token inválido');
    });

    it('should throw INVALID_TOKEN error for token with wrong secret', () => {
      const token = jwt.sign({ userId: 'user-123' }, 'wrong-secret');

      expect(() => authService.verifyToken(token)).toThrow('Token inválido');
    });
  });

  describe('extractTokenFromHeader', () => {
    it('should extract token from valid Bearer header', () => {
      const token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9';
      const authHeader = `Bearer ${token}`;

      const extracted = authService.extractTokenFromHeader(authHeader);

      expect(extracted).toBe(token);
    });

    it('should return null for missing header', () => {
      const extracted = authService.extractTokenFromHeader(undefined);

      expect(extracted).toBeNull();
    });

    it('should return null for header without Bearer prefix', () => {
      const token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9';
      const authHeader = token;

      const extracted = authService.extractTokenFromHeader(authHeader);

      expect(extracted).toBeNull();
    });

    it('should return null for malformed header', () => {
      const authHeader = 'Bearer';

      const extracted = authService.extractTokenFromHeader(authHeader);

      expect(extracted).toBeNull();
    });

    it('should return null for wrong auth type', () => {
      const token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9';
      const authHeader = `Basic ${token}`;

      const extracted = authService.extractTokenFromHeader(authHeader);

      expect(extracted).toBeNull();
    });
  });
});
