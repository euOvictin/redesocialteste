const request = require('supertest');
const app = require('../app');
const authService = require('../services/authService');

describe('Auth Routes', () => {
  describe('POST /api/v1/auth/register', () => {
    it('should register a new user and return tokens', async () => {
      const response = await request(app)
        .post('/api/v1/auth/register')
        .send({
          email: 'newuser@example.com',
          password: 'SecurePass123!',
          name: 'New User'
        })
        .expect(201);

      expect(response.body).toHaveProperty('accessToken');
      expect(response.body).toHaveProperty('refreshToken');
      expect(response.body).toHaveProperty('expiresIn');
      expect(response.body).toHaveProperty('tokenType', 'Bearer');
      expect(response.body).toHaveProperty('user');
      expect(response.body.user).toHaveProperty('email', 'newuser@example.com');
      expect(response.body.user).toHaveProperty('name', 'New User');
    });

    it('should return 400 if email is missing', async () => {
      const response = await request(app)
        .post('/api/v1/auth/register')
        .send({
          password: 'SecurePass123!',
          name: 'New User'
        })
        .expect(400);

      expect(response.body.error).toHaveProperty('code', 'MISSING_FIELDS');
    });

    it('should return 400 if password is missing', async () => {
      const response = await request(app)
        .post('/api/v1/auth/register')
        .send({
          email: 'newuser@example.com',
          name: 'New User'
        })
        .expect(400);

      expect(response.body.error).toHaveProperty('code', 'MISSING_FIELDS');
    });

    it('should return 400 if name is missing', async () => {
      const response = await request(app)
        .post('/api/v1/auth/register')
        .send({
          email: 'newuser@example.com',
          password: 'SecurePass123!'
        })
        .expect(400);

      expect(response.body.error).toHaveProperty('code', 'MISSING_FIELDS');
    });
  });

  describe('POST /api/v1/auth/login', () => {
    it('should login user and return tokens', async () => {
      const response = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: 'user@example.com',
          password: 'SecurePass123!'
        })
        .expect(200);

      expect(response.body).toHaveProperty('accessToken');
      expect(response.body).toHaveProperty('refreshToken');
      expect(response.body).toHaveProperty('expiresIn');
      expect(response.body).toHaveProperty('tokenType', 'Bearer');
      expect(response.body).toHaveProperty('user');
      expect(response.body.user).toHaveProperty('email', 'user@example.com');
    });

    it('should return 400 if email is missing', async () => {
      const response = await request(app)
        .post('/api/v1/auth/login')
        .send({
          password: 'SecurePass123!'
        })
        .expect(400);

      expect(response.body.error).toHaveProperty('code', 'MISSING_CREDENTIALS');
    });

    it('should return 400 if password is missing', async () => {
      const response = await request(app)
        .post('/api/v1/auth/login')
        .send({
          email: 'user@example.com'
        })
        .expect(400);

      expect(response.body.error).toHaveProperty('code', 'MISSING_CREDENTIALS');
    });
  });

  describe('POST /api/v1/auth/refresh', () => {
    it('should refresh tokens with valid refresh token', async () => {
      const tokens = authService.generateTokens('user-123', 'user@example.com');

      const response = await request(app)
        .post('/api/v1/auth/refresh')
        .send({
          refreshToken: tokens.refreshToken
        })
        .expect(200);

      expect(response.body).toHaveProperty('accessToken');
      expect(response.body).toHaveProperty('refreshToken');
      expect(response.body).toHaveProperty('expiresIn');
      expect(response.body).toHaveProperty('tokenType', 'Bearer');
    });

    it('should return 400 if refresh token is missing', async () => {
      const response = await request(app)
        .post('/api/v1/auth/refresh')
        .send({})
        .expect(400);

      expect(response.body.error).toHaveProperty('code', 'MISSING_REFRESH_TOKEN');
    });

    it('should return 401 if refresh token is invalid', async () => {
      const response = await request(app)
        .post('/api/v1/auth/refresh')
        .send({
          refreshToken: 'invalid.token.here'
        })
        .expect(401);

      expect(response.body.error).toHaveProperty('code', 'INVALID_TOKEN');
    });
  });

  describe('POST /api/v1/auth/logout', () => {
    it('should logout user with valid token', async () => {
      const tokens = authService.generateTokens('user-123', 'user@example.com');

      const response = await request(app)
        .post('/api/v1/auth/logout')
        .set('Authorization', `Bearer ${tokens.accessToken}`)
        .expect(200);

      expect(response.body).toHaveProperty('message');
    });

    it('should return 401 if token is missing', async () => {
      const response = await request(app)
        .post('/api/v1/auth/logout')
        .expect(401);

      expect(response.body.error).toHaveProperty('code', 'TOKEN_REQUIRED');
    });

    it('should return 401 if token is invalid', async () => {
      const response = await request(app)
        .post('/api/v1/auth/logout')
        .set('Authorization', 'Bearer invalid.token.here')
        .expect(401);

      expect(response.body.error).toHaveProperty('code', 'INVALID_TOKEN');
    });
  });
});
