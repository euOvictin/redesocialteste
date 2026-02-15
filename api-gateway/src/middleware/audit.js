/**
 * Audit middleware - log suspicious access attempts
 * Requirements: 10.6, 10.7 - Audit and alert on suspicious access
 */
const logger = require('../utils/logger');

// Patterns that might indicate abuse
const SUSPICIOUS_PATTERNS = {
  repeated_401: 'multiple_unauthorized_attempts',
  repeated_404: 'repeated_not_found',
  rapid_requests: 'rate_limit_approach',
  invalid_tokens: 'invalid_token_attempts',
  sql_patterns: 'sql_injection_attempt',
  xss_patterns: 'xss_attempt',
};

// In-memory store for audit (in production use Redis or DB)
const auditLog = [];
const MAX_AUDIT_ENTRIES = 10000;

function addAuditEntry(entry) {
  auditLog.push({
    ...entry,
    timestamp: new Date().toISOString(),
  });
  if (auditLog.length > MAX_AUDIT_ENTRIES) {
    auditLog.shift();
  }
}

function auditMiddleware(req, res, next) {
  const startTime = Date.now();
  
  const originalEnd = res.end;
  res.end = function(chunk, encoding) {
    res.end = originalEnd;
    res.end(chunk, encoding);
    
    // Log suspicious responses
    const duration = Date.now() - startTime;
    const status = res.statusCode;
    const entry = {
      requestId: req.id,
      method: req.method,
      path: req.path,
      ip: req.ip,
      statusCode: status,
      durationMs: duration,
      userId: req.user?.userId,
    };
    
    // Flag suspicious access
    if (status === 401) {
      entry.suspiciousType = SUSPICIOUS_PATTERNS.repeated_401;
      addAuditEntry({ ...entry, type: 'suspicious', severity: 'medium' });
      logger.warn({
        message: 'Suspicious access: Unauthorized attempt',
        ...entry,
      });
    } else if (status === 403) {
      entry.suspiciousType = 'forbidden_access';
      addAuditEntry({ ...entry, type: 'suspicious', severity: 'high' });
      logger.warn({
        message: 'Suspicious access: Forbidden attempt',
        ...entry,
      });
    } else if (status >= 500) {
      addAuditEntry({ ...entry, type: 'error', severity: 'high' });
    }
  };
  
  next();
}

function getAuditLog() {
  return [...auditLog];
}

function getSuspiciousAccessLog() {
  return auditLog.filter(e => e.type === 'suspicious' || e.suspiciousType);
}

module.exports = {
  auditMiddleware,
  addAuditEntry,
  getAuditLog,
  getSuspiciousAccessLog,
  SUSPICIOUS_PATTERNS,
};
