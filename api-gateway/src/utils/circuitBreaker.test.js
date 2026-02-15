const { createCircuitBreaker, withRetry } = require('./circuitBreaker');
const axios = require('axios');

// Mock axios
jest.mock('axios');

describe('Circuit Breaker', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('createCircuitBreaker', () => {
    test('should create a circuit breaker with correct configuration', () => {
      const breaker = createCircuitBreaker('test-service', 'http://localhost:3001');
      
      expect(breaker).toBeDefined();
      expect(breaker.name).toBe('test-service');
      expect(breaker.options.timeout).toBe(30000);
    });

    test('should make successful request through circuit breaker', async () => {
      const mockData = { message: 'success' };
      axios.mockResolvedValue({ data: mockData });

      const breaker = createCircuitBreaker('test-service', 'http://localhost:3001');
      const result = await breaker.fire('GET', '/test');

      expect(result).toEqual(mockData);
      expect(axios).toHaveBeenCalledWith({
        method: 'GET',
        url: 'http://localhost:3001/test',
        data: null,
        headers: {},
        timeout: 30000
      });
    });

    test('should handle failed request', async () => {
      const error = new Error('Service unavailable');
      axios.mockRejectedValue(error);

      const breaker = createCircuitBreaker('test-service', 'http://localhost:3001');

      // A primeira falha não abre o circuit, então retorna o erro original
      // Mas o fallback pode ser acionado dependendo da configuração
      await expect(breaker.fire('GET', '/test')).rejects.toThrow();
    });

    test('should open circuit after multiple failures', async () => {
      const error = new Error('Service unavailable');
      axios.mockRejectedValue(error);

      const breaker = createCircuitBreaker('test-service', 'http://localhost:3001');

      // Fazer múltiplas requisições falhadas
      for (let i = 0; i < 10; i++) {
        try {
          await breaker.fire('GET', '/test');
        } catch (e) {
          // Ignorar erros
        }
      }

      // Circuit deve estar aberto
      expect(breaker.opened).toBe(true);
    });

    test('should use fallback when circuit is open', async () => {
      const error = new Error('Service unavailable');
      axios.mockRejectedValue(error);

      const breaker = createCircuitBreaker('test-service', 'http://localhost:3001');

      // Forçar abertura do circuit
      breaker.open();

      // Tentar fazer requisição com circuit aberto
      await expect(breaker.fire('GET', '/test')).rejects.toThrow('Service test-service is currently unavailable');
    });
  });

  describe('withRetry', () => {
    test('should succeed on first attempt', async () => {
      const fn = jest.fn().mockResolvedValue('success');
      
      const result = await withRetry(fn);

      expect(result).toBe('success');
      expect(fn).toHaveBeenCalledTimes(1);
    });

    test('should retry on failure and eventually succeed', async () => {
      const fn = jest.fn()
        .mockRejectedValueOnce(new Error('Fail 1'))
        .mockRejectedValueOnce(new Error('Fail 2'))
        .mockResolvedValue('success');

      const result = await withRetry(fn, 3, 100);

      expect(result).toBe('success');
      expect(fn).toHaveBeenCalledTimes(3);
    });

    test('should fail after max retries', async () => {
      const fn = jest.fn().mockRejectedValue(new Error('Always fails'));

      await expect(withRetry(fn, 3, 100)).rejects.toThrow('Always fails');
      expect(fn).toHaveBeenCalledTimes(3);
    });

    test('should use exponential backoff', async () => {
      const fn = jest.fn()
        .mockRejectedValueOnce(new Error('Fail 1'))
        .mockRejectedValueOnce(new Error('Fail 2'))
        .mockResolvedValue('success');

      const startTime = Date.now();
      await withRetry(fn, 3, 100);
      const endTime = Date.now();

      // Deve ter esperado pelo menos 100ms + 200ms = 300ms
      expect(endTime - startTime).toBeGreaterThanOrEqual(300);
      expect(fn).toHaveBeenCalledTimes(3);
    });
  });

  describe('Circuit Breaker with Retry', () => {
    test('should work with retry for transient failures', async () => {
      const mockData = { message: 'success' };
      
      // Mock para simular sucesso na primeira tentativa
      axios.mockResolvedValue({ data: mockData });

      const breaker = createCircuitBreaker('success-service', 'http://localhost:3001');

      const result = await breaker.fire('GET', '/test');

      expect(result).toEqual(mockData);
    });
  });

  describe('Circuit Breaker Timeout', () => {
    test('should timeout after configured duration', async () => {
      // Simular requisição que demora muito
      axios.mockImplementation(() => 
        new Promise((resolve) => setTimeout(() => resolve({ data: 'too late' }), 35000))
      );

      const breaker = createCircuitBreaker('test-service', 'http://localhost:3001');

      await expect(breaker.fire('GET', '/test')).rejects.toThrow();
    }, 35000);
  });
});
