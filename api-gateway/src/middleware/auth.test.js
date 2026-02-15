const { authenticate, optionalAuth } = require('./auth');
const authService = require('../services/authService');

describe('Auth Middleware', () => {
  describe('authenticate', () => {
    it('should authenticate valid token and add user to request', () => {
      const userId = 'user-123';
      const email = 'test@example.com';
      const tokens = authService.generateTokens(userId, email);

      const req = {
        headers: {
          authorization: `Bearer ${tokens.accessToken}`
        },
        id: 'request-123'
      };
      const res = {};
      const next = jest.fn();

      authenticate(req, res, next);

      expect(req.user).toBeDefined();
      expect(req.user.userId).toBe(userId);
      expect(req.user.email).toBe(email);
      expect(next).toHaveBeenCalledWith();
    });

    it('should call next with error if token is missing', () => {
      const req = {
        headers: {},
        id: 'request-123'
      };
      const res = {};
      const next = jest.fn();

      authenticate(req, res, next);

      expect(next).toHaveBeenCalledWith(
        expect.objectContaining({
          message: 'Token não fornecido',
          statusCode: 401,
          code: 'TOKEN_REQUIRED'
        })
      );
    });

    it('should call next with error if token is invalid', () => {
      const req = {
        headers: {
          authorization: 'Bearer invalid.token.here'
        },
        id: 'request-123'
      };
      const res = {};
      const next = jest.fn();

      authenticate(req, res, next);

      expect(next).toHaveBeenCalledWith(
        expect.objectContaining({
          message: 'Token inválido',
          statusCode: 401,
          code: 'INVALID_TOKEN'
        })
      );
    });

    it('should call next with error if authorization header is malformed', () => {
      const req = {
        headers: {
          authorization: 'InvalidFormat'
        },
        id: 'request-123'
      };
      const res = {};
      const next = jest.fn();

      authenticate(req, res, next);

      expect(next).toHaveBeenCalledWith(
        expect.objectContaining({
          message: 'Token não fornecido',
          statusCode: 401,
          code: 'TOKEN_REQUIRED'
        })
      );
    });
  });

  describe('optionalAuth', () => {
    it('should add user to request if valid token is provided', () => {
      const userId = 'user-123';
      const email = 'test@example.com';
      const tokens = authService.generateTokens(userId, email);

      const req = {
        headers: {
          authorization: `Bearer ${tokens.accessToken}`
        }
      };
      const res = {};
      const next = jest.fn();

      optionalAuth(req, res, next);

      expect(req.user).toBeDefined();
      expect(req.user.userId).toBe(userId);
      expect(req.user.email).toBe(email);
      expect(next).toHaveBeenCalledWith();
    });

    it('should not add user to request if token is missing', () => {
      const req = {
        headers: {}
      };
      const res = {};
      const next = jest.fn();

      optionalAuth(req, res, next);

      expect(req.user).toBeUndefined();
      expect(next).toHaveBeenCalledWith();
    });

    it('should not add user to request if token is invalid', () => {
      const req = {
        headers: {
          authorization: 'Bearer invalid.token.here'
        }
      };
      const res = {};
      const next = jest.fn();

      optionalAuth(req, res, next);

      expect(req.user).toBeUndefined();
      expect(next).toHaveBeenCalledWith();
    });

    it('should continue without error even if token is invalid', () => {
      const req = {
        headers: {
          authorization: 'Bearer invalid.token.here'
        }
      };
      const res = {};
      const next = jest.fn();

      optionalAuth(req, res, next);

      // Deve chamar next sem argumentos (sem erro)
      expect(next).toHaveBeenCalledWith();
      expect(next.mock.calls[0]).toHaveLength(0);
    });
  });
});
