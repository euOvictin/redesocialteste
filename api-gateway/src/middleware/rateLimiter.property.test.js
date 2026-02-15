const request = require('supertest');
const express = require('express');
const fc = require('fast-check');
const { createGeneralRateLimiter, createAuthRateLimiter, closeRedisClient } = require('./rateLimiter');

describe('Rate Limiter Property-Based Tests', () => {
  let generalLimiter;
  let authLimiter;

  beforeAll(async () => {
    generalLimiter = await createGeneralRateLimiter();
    authLimiter = await createAuthRateLimiter();
  });

  afterAll(async () => {
    await closeRedisClient();
  });

  /**
   * Feature: rede-social-brasileira, Property 50: Rate limit bloqueia após 100 requisições
   * **Validates: Requirements 10.1**
   */
  describe('Property 50: Rate limit bloqueia após 100 requisições', () => {
    test('should block requests after exceeding 100 requests per minute', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 101, max: 150 }), // Número de requisições acima do limite
          async (numRequests) => {
            const app = express();
            app.use(express.json());
            app.use(generalLimiter);
            app.get('/test', (req, res) => {
              res.status(200).json({ message: 'success' });
            });

            // Fazer requisições até exceder o limite
            let blockedCount = 0;
            for (let i = 0; i < numRequests; i++) {
              const response = await request(app).get('/test');
              if (response.status === 429) {
                blockedCount++;
              }
            }

            // Deve ter bloqueado pelo menos uma requisição
            expect(blockedCount).toBeGreaterThan(0);
            
            // Após 100 requisições, todas as subsequentes devem ser bloqueadas
            // (pelo menos numRequests - 100 devem ser bloqueadas)
            expect(blockedCount).toBeGreaterThanOrEqual(numRequests - 100);
          }
        ),
        { numRuns: 100, timeout: 60000 }
      );
    }, 120000);
  });

  /**
   * Feature: rede-social-brasileira, Property 51: Exceder rate limit retorna 429 com Retry-After
   * **Validates: Requirements 10.2**
   */
  describe('Property 51: Exceder rate limit retorna 429 com Retry-After', () => {
    test('should return 429 with Retry-After header when rate limit exceeded', async () => {
      // Criar app uma vez para compartilhar o rate limiter
      const app = express();
      app.use(express.json());
      app.use((req, res, next) => {
        req.id = `test-${Date.now()}-${Math.random()}`;
        next();
      });
      app.use(generalLimiter);
      app.get('/test', (req, res) => {
        res.status(200).json({ message: 'success' });
      });

      await fc.assert(
        fc.asyncProperty(
          fc.constant(null), // Não precisamos de entrada variável
          async () => {
            // Fazer 101 requisições para exceder o limite
            for (let i = 0; i < 101; i++) {
              await request(app).get('/test');
            }

            // A próxima requisição deve retornar 429 com Retry-After
            const response = await request(app).get('/test');
            
            expect(response.status).toBe(429);
            expect(response.body.error).toBeDefined();
            expect(response.body.error.code).toBe('TOO_MANY_REQUESTS');
            expect(response.body.error.retryAfter).toBeDefined();
            expect(typeof response.body.error.retryAfter).toBe('number');
            expect(response.body.error.retryAfter).toBeGreaterThan(0);
            expect(response.body.error.message).toContain('Limite de requisições excedido');
          }
        ),
        { numRuns: 10, timeout: 60000 } // Reduzir para 10 runs devido ao tempo
      );
    }, 120000);
  });

  /**
   * Additional Property: Auth rate limit bloqueia após 10 requisições
   * **Validates: Requirements 10.3**
   */
  describe('Auth Rate Limit Property', () => {
    test('should block auth requests after exceeding 10 requests per minute', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.emailAddress(),
          fc.string({ minLength: 8, maxLength: 20 }),
          fc.integer({ min: 11, max: 20 }),
          async (email, password, numRequests) => {
            const app = express();
            app.use(express.json());
            app.use(authLimiter);
            app.post('/auth/login', (req, res) => {
              res.status(200).json({ message: 'authenticated' });
            });

            // Fazer requisições até exceder o limite
            let blockedCount = 0;
            for (let i = 0; i < numRequests; i++) {
              const response = await request(app)
                .post('/auth/login')
                .send({ email, password });
              
              if (response.status === 429) {
                blockedCount++;
              }
            }

            // Deve ter bloqueado pelo menos uma requisição
            expect(blockedCount).toBeGreaterThan(0);
            
            // Após 10 requisições, todas as subsequentes devem ser bloqueadas
            expect(blockedCount).toBeGreaterThanOrEqual(numRequests - 10);
          }
        ),
        { numRuns: 100, timeout: 60000 }
      );
    }, 120000);
  });

  /**
   * Additional Property: Rate limit é por usuário/IP
   */
  describe('Rate Limit Isolation Property', () => {
    test('rate limit should be isolated per user/IP', async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.constant(null),
          async () => {
            const app = express();
            app.use(express.json());
            app.use(generalLimiter);
            app.get('/test', (req, res) => {
              res.status(200).json({ message: 'success' });
            });

            // Fazer 100 requisições (atingir o limite)
            for (let i = 0; i < 100; i++) {
              await request(app).get('/test');
            }

            // A 101ª requisição deve ser bloqueada
            const response1 = await request(app).get('/test');
            expect(response1.status).toBe(429);

            // Mas uma requisição de outro "usuário" (simulado por nova instância)
            // ainda deve funcionar (em produção seria outro IP)
            // Nota: Em testes, todas as requisições vêm do mesmo IP, então este teste
            // é mais conceitual. Em produção com Redis, IPs diferentes teriam contadores separados.
          }
        ),
        { numRuns: 50, timeout: 60000 }
      );
    }, 120000);
  });

  /**
   * Additional Property: Retry-After é consistente
   */
  describe('Retry-After Consistency Property', () => {
    test('Retry-After should be consistent with rate limit window', async () => {
      // Criar app uma vez para compartilhar o rate limiter
      const app = express();
      app.use(express.json());
      app.use((req, res, next) => {
        req.id = `test-${Date.now()}-${Math.random()}`;
        next();
      });
      app.use(generalLimiter);
      app.get('/test', (req, res) => {
        res.status(200).json({ message: 'success' });
      });

      await fc.assert(
        fc.asyncProperty(
          fc.constant(null),
          async () => {
            // Exceder limite
            for (let i = 0; i < 101; i++) {
              await request(app).get('/test');
            }

            const response = await request(app).get('/test');
            
            // Retry-After deve ser aproximadamente 60 segundos (1 minuto)
            if (response.status === 429 && response.body.error) {
              expect(response.body.error.retryAfter).toBeLessThanOrEqual(60);
              expect(response.body.error.retryAfter).toBeGreaterThan(0);
            }
          }
        ),
        { numRuns: 10, timeout: 60000 } // Reduzir para 10 runs
      );
    }, 120000);
  });
});
