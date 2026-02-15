/**
 * Sanitization middleware - prevent SQL injection, XSS, code injection
 * Requirements: 10.4 - Sanitize all user inputs
 */
const logger = require('../utils/logger');

// Patterns indicative of SQL injection (narrow - avoid false positives)
const SQL_INJECTION_PATTERNS = [
  /;\s*DROP\s+TABLE/gi,
  /;\s*DELETE\s+FROM/gi,
  /UNION\s+SELECT/gi,
  /'\s*OR\s*'1'\s*=\s*'1/gi,
  /1\s*=\s*1\s*(--|#|\/\*)/gi,
  /(\%\27|\%22|\%3B)/gi,
];

// Patterns indicative of XSS
const XSS_PATTERNS = [
  /<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi,
  /javascript:/gi,
  /vbscript:/gi,
  /on\w+\s*=/gi,  // onclick=, onerror=, etc
  /<iframe/gi,
  /<object/gi,
  /<embed/gi,
  /<svg\s+on/gi,
  /expression\s*\(/gi,
];

// Patterns indicative of code injection / command injection
const CODE_INJECTION_PATTERNS = [
  /\beval\s*\(/gi,
  /\bexec\s*\(/gi,
  /\bFunction\s*\(/gi,
  /`[^`]*\$\{[^}]+\}[^`]*`/g,
];

function containsMaliciousContent(value) {
  if (typeof value !== 'string') return false;
  const str = String(value);
  
  for (const pattern of SQL_INJECTION_PATTERNS) {
    if (pattern.test(str)) return { type: 'sql_injection', pattern: pattern.toString() };
  }
  for (const pattern of XSS_PATTERNS) {
    if (pattern.test(str)) return { type: 'xss', pattern: pattern.toString() };
  }
  for (const pattern of CODE_INJECTION_PATTERNS) {
    if (pattern.test(str)) return { type: 'code_injection', pattern: pattern.toString() };
  }
  return false;
}

function sanitizeValue(value) {
  if (value === null || value === undefined) return value;
  if (typeof value === 'number' || typeof value === 'boolean') return value;
  if (typeof value === 'object') return value; // Arrays/objects handled recursively
  
  let str = String(value);
  // Basic HTML entity encoding
  str = str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;')
    .replace(/\//g, '&#x2F;');
  return str;
}

function deepSanitize(obj, path = '') {
  if (obj === null || obj === undefined) return obj;
  
  if (typeof obj === 'string') {
    const malicious = containsMaliciousContent(obj);
    if (malicious) return null; // Will be caught by validation
    return sanitizeValue(obj);
  }
  
  if (Array.isArray(obj)) {
    return obj.map((item, i) => deepSanitize(item, `${path}[${i}]`));
  }
  
  if (typeof obj === 'object') {
    const result = {};
    for (const [key, val] of Object.entries(obj)) {
      result[key] = deepSanitize(val, `${path}.${key}`);
    }
    return result;
  }
  
  return obj;
}

function sanitizationMiddleware(req, res, next) {
  const suspicious = [];
  
  // Check query params
  if (req.query && typeof req.query === 'object') {
    for (const [key, val] of Object.entries(req.query)) {
      if (val && containsMaliciousContent(String(val))) {
        suspicious.push({ location: `query.${key}`, value: String(val).substring(0, 50) });
      }
    }
  }
  
  // Check body
  if (req.body && typeof req.body === 'object') {
    const checkBody = (obj, path = 'body') => {
      if (!obj || typeof obj !== 'object') return;
      for (const [key, val] of Object.entries(obj)) {
        if (typeof val === 'string' && containsMaliciousContent(val)) {
          suspicious.push({ location: `${path}.${key}`, value: val.substring(0, 50) });
        } else if (val && typeof val === 'object' && !Array.isArray(val)) {
          checkBody(val, `${path}.${key}`);
        }
      }
    };
    checkBody(req.body);
  }
  
  // Don't mutate body - only reject malicious input
  if (suspicious.length > 0) {
    logger.warn({
      message: 'Suspicious input detected - rejected',
      requestId: req.id,
      ip: req.ip,
      path: req.path,
      suspicious,
    });
    return res.status(400).json({
      error: {
        code: 'INVALID_INPUT',
        message: 'Input contém conteúdo não permitido',
        requestId: req.id,
        timestamp: new Date().toISOString(),
      },
    });
  }
  
  next();
}

module.exports = { sanitizationMiddleware, containsMaliciousContent, sanitizeValue };
