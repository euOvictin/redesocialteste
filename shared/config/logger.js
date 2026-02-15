// Configuração de logging estruturado para microsserviços Node.js
// Usa Winston para logging e integração com Jaeger para distributed tracing

const winston = require('winston');
const { format } = winston;

// Formato customizado para logs estruturados
const structuredFormat = format.combine(
  format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
  format.errors({ stack: true }),
  format.metadata({ fillExcept: ['message', 'level', 'timestamp', 'service'] }),
  format.json()
);

// Formato para console (desenvolvimento)
const consoleFormat = format.combine(
  format.colorize(),
  format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
  format.printf(({ timestamp, level, message, service, ...metadata }) => {
    let msg = `${timestamp} [${service}] ${level}: ${message}`;
    if (Object.keys(metadata).length > 0) {
      msg += ` ${JSON.stringify(metadata)}`;
    }
    return msg;
  })
);

/**
 * Cria um logger configurado para o microsserviço
 * @param {string} serviceName - Nome do microsserviço
 * @returns {winston.Logger} Logger configurado
 */
function createLogger(serviceName) {
  const logLevel = process.env.LOG_LEVEL || 'info';
  const logFormat = process.env.LOG_FORMAT || 'json';

  const transports = [
    new winston.transports.Console({
      format: logFormat === 'json' ? structuredFormat : consoleFormat
    })
  ];

  // Em produção, adicionar transporte para arquivo ou serviço externo
  if (process.env.NODE_ENV === 'production') {
    transports.push(
      new winston.transports.File({
        filename: `logs/${serviceName}-error.log`,
        level: 'error',
        format: structuredFormat
      }),
      new winston.transports.File({
        filename: `logs/${serviceName}-combined.log`,
        format: structuredFormat
      })
    );
  }

  return winston.createLogger({
    level: logLevel,
    defaultMeta: { service: serviceName },
    transports
  });
}

/**
 * Middleware Express para logging de requisições
 * @param {winston.Logger} logger - Logger do Winston
 */
function requestLogger(logger) {
  return (req, res, next) => {
    const startTime = Date.now();

    // Log da requisição
    logger.info('Incoming request', {
      method: req.method,
      path: req.path,
      query: req.query,
      ip: req.ip,
      userAgent: req.get('user-agent'),
      traceId: req.headers['x-trace-id']
    });

    // Interceptar o fim da resposta
    res.on('finish', () => {
      const duration = Date.now() - startTime;
      const logLevel = res.statusCode >= 400 ? 'error' : 'info';

      logger.log(logLevel, 'Request completed', {
        method: req.method,
        path: req.path,
        statusCode: res.statusCode,
        duration: `${duration}ms`,
        traceId: req.headers['x-trace-id']
      });
    });

    next();
  };
}

/**
 * Middleware Express para adicionar trace ID
 */
function traceIdMiddleware(req, res, next) {
  const traceId = req.headers['x-trace-id'] || generateTraceId();
  req.traceId = traceId;
  res.setHeader('X-Trace-Id', traceId);
  next();
}

/**
 * Gera um trace ID único
 */
function generateTraceId() {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

module.exports = {
  createLogger,
  requestLogger,
  traceIdMiddleware
};
