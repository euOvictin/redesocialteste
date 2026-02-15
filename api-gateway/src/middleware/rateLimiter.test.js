const request = require('supertest');
const express = require('express');
const { createGeneralRateLimiter, createAuthRateLimiter, closeRedisClient } = require('./rateLimiter');

describe('Rate Limiter Middleware', () => {
  let app;
  let generalLimiter;
  let authLimiter;

  beforeAll(async () => {
    // Inicializar rate limiters
    generalLimiter = await createGeneralRateLimiter();
    authLimiter = await createAuthRateLimiter();
  });

  afterAll(async () => {
    // Fechar conexão Redis
    await closeRedisClient();
  });

  describe('General Rate Limiter', () => {
    beforeEach(() => {
      app = express();
      app.use(express.json());
      app.use(generalLimiter);
      app.get('/test', (req, res) => {
        res.status(200).json({ message: 'success' });
      });
    });

    test('should allow requests within limit', async () => {
      const response = await request(app).get('/test');
      expect(response.status).toBe(200);
      expect(response.body.message).toBe('success');
    });

    test('should return 429 when limit exceeded', async () => {
      // Fazer 100 requisições (limite)
      for (let i = 0; i < 100; i++) {
        await request(app).get('/test');
      }

      // 101ª requisição deve ser bloqueada
      const response = await request(app).get('/test');
      expect(response.status).toBe(429);
      expect(response.body.error.code).toBe('TOO_MANY_REQUESTS');
    }, 30000); // Timeout maior para este teste

    test('should include Retry-After header when limit exceeded', async () => {
      // Fazer 100 requisições (limite)
      for (let i = 0; i < 100; i++) {
        await request(app).get('/test');
      }

      // 101ª requisição deve incluir Retry-After
      const response = await request(app).get('/test');
      expect(response.status).toBe(429);
      expect(response.body.error.retryAfter).toBeDefined();
      expect(typeof response.body.error.retryAfter).toBe('number');
    }, 30000);
  });

  describe('Auth Rate Limiter', () => {
    beforeEach(() => {
      app = express();
      app.use(express.json());
      app.use(authLimiter);
      app.post('/auth/login', (req, res) => {
        res.status(200).json({ message: 'authenticated' });
      });
    });

    test('should allow auth requests within limit', async () => {
      const response = await request(app)
        .post('/auth/login')
        .send({ email: 'test@example.com', password: 'password' });
      
      expect(response.status).toBe(200);
      expect(response.body.message).toBe('authenticated');
    });

    test('should return 429 when auth limit exceeded', async () => {
      const email = `test-${Date.now()}@example.com`;
      
      // Fazer 10 requisições (limite para auth)
      for (let i = 0; i < 10; i++) {
        await request(app)
          .post('/auth/login')
          .send({ email, password: 'password' });
      }

      // 11ª requisição deve ser bloqueada
      const response = await request(app)
        .post('/auth/login')
        .send({ email, password: 'password' });
      
      expect(response.status).toBe(429);
      expect(response.body.error.code).toBe('TOO_MANY_REQUESTS');
    }, 30000);

    test('should include descriptive message for auth rate limit', async () => {
      const email = `test-${Date.now()}@example.com`;
      
      // Fazer 10 requisições (limite para auth)
      for (let i = 0; i < 10; i++) {
        await request(app)
          .post('/auth/login')
          .send({ email, password: 'password' });
      }

      // 11ª requisição deve ter mensagem descritiva
      const response = await request(app)
        .post('/auth/login')
        .send({ email, password: 'password' });
      
      expect(response.status).toBe(429);
      expect(response.body.error.message).toContain('autenticação');
    }, 30000);
  });

  describe('Rate Limiter Error Response Format', () => {
    beforeEach(() => {
      app = express();
      app.use(express.json());
      // Adicionar middleware de request ID
      app.use((req, res, next) => {
        req.id = 'test-request-id';
        next();
      });
      app.use(generalLimiter);
      app.get('/test', (req, res) => {
        res.status(200).json({ message: 'success' });
      });
    });

    test('should return standardized error format', async () => {
      // Exceder limite
      for (let i = 0; i < 101; i++) {
        await request(app).get('/test');
      }

      const response = await request(app).get('/test');
      
      expect(response.status).toBe(429);
      expect(response.body.error).toBeDefined();
      expect(response.body.error.code).toBe('TOO_MANY_REQUESTS');
      expect(response.body.error.message).toBeDefined();
      expect(response.body.error.retryAfter).toBeDefined();
      expect(response.body.error.timestamp).toBeDefined();
    }, 30000);
  });
});
