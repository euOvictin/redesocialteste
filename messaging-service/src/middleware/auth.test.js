const jwt = require('jsonwebtoken');
const { authenticateHTTP, authenticateWebSocket } = require('./auth');
const config = require('../config');

describe('Auth Middleware Tests', () => {
  describe('authenticateHTTP', () => {
    let req, res, next;

    beforeEach(() => {
      req = {
        headers: {}
      };
      res = {
        status: jest.fn().mockReturnThis(),
        json: jest.fn()
      };
      next = jest.fn();
    });

    it('should reject request without Authorization header', () => {
      authenticateHTTP(req, res, next);

      expect(res.status).toHaveBeenCalledWith(401);
      expect(res.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'UNAUTHORIZED',
            message: 'Token de autenticação não fornecido'
          })
        })
      );
      expect(next).not.toHaveBeenCalled();
    });

    it('should reject request with malformed Authorization header', () => {
      req.headers.authorization = 'InvalidFormat token123';

      authenticateHTTP(req, res, next);

      expect(res.status).toHaveBeenCalledWith(401);
      expect(res.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'UNAUTHORIZED'
          })
        })
      );
      expect(next).not.toHaveBeenCalled();
    });

    it('should reject request with invalid token', () => {
      req.headers.authorization = 'Bearer invalid-token';

      authenticateHTTP(req, res, next);

      expect(res.status).toHaveBeenCalledWith(401);
      expect(res.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'INVALID_TOKEN',
            message: 'Token de autenticação inválido ou expirado'
          })
        })
      );
      expect(next).not.toHaveBeenCalled();
    });

    it('should reject request with expired token', () => {
      const expiredToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '-1h' }
      );

      req.headers.authorization = `Bearer ${expiredToken}`;

      authenticateHTTP(req, res, next);

      expect(res.status).toHaveBeenCalledWith(401);
      expect(res.json).toHaveBeenCalledWith(
        expect.objectContaining({
          error: expect.objectContaining({
            code: 'INVALID_TOKEN'
          })
        })
      );
      expect(next).not.toHaveBeenCalled();
    });

    it('should accept request with valid token', () => {
      const validToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      req.headers.authorization = `Bearer ${validToken}`;

      authenticateHTTP(req, res, next);

      expect(req.user).toEqual({
        userId: 'user-123',
        email: 'test@example.com'
      });
      expect(next).toHaveBeenCalled();
      expect(res.status).not.toHaveBeenCalled();
      expect(res.json).not.toHaveBeenCalled();
    });

    it('should extract userId and email from token', () => {
      const validToken = jwt.sign(
        { userId: 'user-456', email: 'another@example.com' },
        config.jwt.secret,
        { expiresIn: '2h' }
      );

      req.headers.authorization = `Bearer ${validToken}`;

      authenticateHTTP(req, res, next);

      expect(req.user.userId).toBe('user-456');
      expect(req.user.email).toBe('another@example.com');
      expect(next).toHaveBeenCalled();
    });
  });

  describe('authenticateWebSocket', () => {
    it('should return null for null token', () => {
      const result = authenticateWebSocket(null);
      expect(result).toBeNull();
    });

    it('should return null for undefined token', () => {
      const result = authenticateWebSocket(undefined);
      expect(result).toBeNull();
    });

    it('should return null for empty string token', () => {
      const result = authenticateWebSocket('');
      expect(result).toBeNull();
    });

    it('should return null for invalid token', () => {
      const result = authenticateWebSocket('invalid-token');
      expect(result).toBeNull();
    });

    it('should return null for expired token', () => {
      const expiredToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '-1h' }
      );

      const result = authenticateWebSocket(expiredToken);
      expect(result).toBeNull();
    });

    it('should return null for token with wrong secret', () => {
      const wrongToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        'wrong-secret',
        { expiresIn: '1h' }
      );

      const result = authenticateWebSocket(wrongToken);
      expect(result).toBeNull();
    });

    it('should return user data for valid token', () => {
      const validToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const result = authenticateWebSocket(validToken);

      expect(result).not.toBeNull();
      expect(result).toEqual({
        userId: 'user-123',
        email: 'test@example.com'
      });
    });

    it('should extract only userId and email from token', () => {
      const validToken = jwt.sign(
        { 
          userId: 'user-456', 
          email: 'test@example.com',
          extraField: 'should-not-be-included',
          anotherField: 123
        },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const result = authenticateWebSocket(validToken);

      expect(result).toEqual({
        userId: 'user-456',
        email: 'test@example.com'
      });
      expect(result).not.toHaveProperty('extraField');
      expect(result).not.toHaveProperty('anotherField');
    });

    it('should handle tokens with different expiration times', () => {
      const shortToken = jwt.sign(
        { userId: 'user-1', email: 'short@example.com' },
        config.jwt.secret,
        { expiresIn: '1m' }
      );

      const longToken = jwt.sign(
        { userId: 'user-2', email: 'long@example.com' },
        config.jwt.secret,
        { expiresIn: '24h' }
      );

      const result1 = authenticateWebSocket(shortToken);
      const result2 = authenticateWebSocket(longToken);

      expect(result1).not.toBeNull();
      expect(result2).not.toBeNull();
      expect(result1.userId).toBe('user-1');
      expect(result2.userId).toBe('user-2');
    });

    it('should be deterministic for the same token', () => {
      const validToken = jwt.sign(
        { userId: 'user-123', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const result1 = authenticateWebSocket(validToken);
      const result2 = authenticateWebSocket(validToken);
      const result3 = authenticateWebSocket(validToken);

      expect(result1).toEqual(result2);
      expect(result2).toEqual(result3);
    });
  });

  describe('Edge Cases', () => {
    it('should handle token with special characters in userId', () => {
      const validToken = jwt.sign(
        { userId: 'user-123-abc_def', email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const result = authenticateWebSocket(validToken);

      expect(result).not.toBeNull();
      expect(result.userId).toBe('user-123-abc_def');
    });

    it('should handle token with unicode characters', () => {
      const validToken = jwt.sign(
        { userId: 'user-123', email: 'tëst@éxample.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const result = authenticateWebSocket(validToken);

      expect(result).not.toBeNull();
      expect(result.email).toBe('tëst@éxample.com');
    });

    it('should handle token with very long userId', () => {
      const longUserId = 'a'.repeat(1000);
      const validToken = jwt.sign(
        { userId: longUserId, email: 'test@example.com' },
        config.jwt.secret,
        { expiresIn: '1h' }
      );

      const result = authenticateWebSocket(validToken);

      expect(result).not.toBeNull();
      expect(result.userId).toBe(longUserId);
    });
  });
});
