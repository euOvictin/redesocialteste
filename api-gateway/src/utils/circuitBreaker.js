const CircuitBreaker = require('opossum');
const axios = require('axios');
const config = require('../config');
const logger = require('./logger');

/**
 * Cria um circuit breaker para chamadas HTTP a serviços downstream
 * 
 * Configuração:
 * - Timeout: 30 segundos
 * - Abre após 5 falhas consecutivas (50% de erro)
 * - Fecha após 30 segundos
 * - Retry com backoff exponencial (até 3 tentativas)
 * 
 * @param {string} serviceName - Nome do serviço (para logging)
 * @param {string} serviceUrl - URL base do serviço
 * @returns {CircuitBreaker} Circuit breaker configurado
 */
function createCircuitBreaker(serviceName, serviceUrl) {
  const options = {
    timeout: config.circuitBreaker.timeout, // 30 segundos
    errorThresholdPercentage: config.circuitBreaker.errorThresholdPercentage, // 50%
    resetTimeout: config.circuitBreaker.resetTimeout, // 30 segundos
    name: serviceName
  };

  // Função que será protegida pelo circuit breaker
  async function makeRequest(method, path, data = null, headers = {}) {
    const url = `${serviceUrl}${path}`;
    
    logger.info({
      message: 'Making downstream request',
      service: serviceName,
      method,
      url
    });

    try {
      const response = await axios({
        method,
        url,
        data,
        headers,
        timeout: config.circuitBreaker.timeout
      });

      return response.data;
    } catch (error) {
      logger.error({
        message: 'Downstream request failed',
        service: serviceName,
        method,
        url,
        error: error.message,
        status: error.response?.status
      });

      throw error;
    }
  }

  const breaker = new CircuitBreaker(makeRequest, options);

  // Event listeners para logging
  breaker.on('open', () => {
    logger.warn({
      message: 'Circuit breaker opened',
      service: serviceName,
      reason: 'Too many failures'
    });
  });

  breaker.on('halfOpen', () => {
    logger.info({
      message: 'Circuit breaker half-open',
      service: serviceName,
      reason: 'Attempting to recover'
    });
  });

  breaker.on('close', () => {
    logger.info({
      message: 'Circuit breaker closed',
      service: serviceName,
      reason: 'Service recovered'
    });
  });

  breaker.on('failure', (error) => {
    logger.error({
      message: 'Circuit breaker failure',
      service: serviceName,
      error: error.message
    });
  });

  breaker.on('timeout', () => {
    logger.warn({
      message: 'Circuit breaker timeout',
      service: serviceName,
      timeout: config.circuitBreaker.timeout
    });
  });

  breaker.on('reject', () => {
    logger.warn({
      message: 'Circuit breaker rejected request',
      service: serviceName,
      reason: 'Circuit is open'
    });
  });

  // Fallback para quando o circuit breaker está aberto
  breaker.fallback(() => {
    logger.warn({
      message: 'Circuit breaker fallback triggered',
      service: serviceName
    });

    throw new Error(`Service ${serviceName} is currently unavailable`);
  });

  return breaker;
}

/**
 * Executa uma requisição com retry e backoff exponencial
 * 
 * @param {Function} fn - Função assíncrona a ser executada
 * @param {number} maxRetries - Número máximo de tentativas (padrão: 3)
 * @param {number} initialDelay - Delay inicial em ms (padrão: 1000)
 * @returns {Promise} Resultado da função
 */
async function withRetry(fn, maxRetries = 3, initialDelay = 1000) {
  let lastError;
  
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error;
      
      if (attempt < maxRetries) {
        // Backoff exponencial: 1s, 2s, 4s
        const delay = initialDelay * Math.pow(2, attempt - 1);
        
        logger.warn({
          message: 'Retry attempt',
          attempt,
          maxRetries,
          delay,
          error: error.message
        });

        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
  }

  logger.error({
    message: 'All retry attempts failed',
    maxRetries,
    error: lastError.message
  });

  throw lastError;
}

/**
 * Cria circuit breakers para todos os serviços configurados
 * 
 * @returns {Object} Objeto com circuit breakers para cada serviço
 */
function createServiceBreakers() {
  const breakers = {};

  Object.keys(config.services).forEach(serviceName => {
    const serviceUrl = config.services[serviceName];
    breakers[serviceName] = createCircuitBreaker(serviceName, serviceUrl);
  });

  return breakers;
}

module.exports = {
  createCircuitBreaker,
  withRetry,
  createServiceBreakers
};
