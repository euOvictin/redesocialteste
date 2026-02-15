/**
 * Property 53: Acessos suspeitos são registrados
 * Validates: Requirements 10.6
 */
const fc = require('fast-check');
const { addAuditEntry, getAuditLog, getSuspiciousAccessLog, SUSPICIOUS_PATTERNS } = require('./audit');

describe('Audit - Property-Based Tests', () => {
  describe('Property 53: Acessos suspeitos são registrados', () => {
    it('addAuditEntry stores entries with timestamp', () => {
      fc.assert(
        fc.property(
          fc.uuid(),
          fc.string({ minLength: 1, maxLength: 50 }),
          fc.integer({ min: 0, max: 999 }),
          (requestId, path, statusCode) => {
            addAuditEntry({
              requestId,
              path,
              statusCode,
              type: 'suspicious',
              suspiciousType: SUSPICIOUS_PATTERNS.repeated_401,
            });
            const log = getAuditLog();
            const last = log[log.length - 1];
            expect(last.requestId).toBe(requestId);
            expect(last.path).toBe(path);
            expect(last.statusCode).toBe(statusCode);
            expect(last.timestamp).toBeDefined();
          }
        ),
        { numRuns: 20 }
      );
    });

    it('suspicious entries appear in getSuspiciousAccessLog', () => {
      addAuditEntry({
        requestId: 'test-123',
        path: '/api/auth/login',
        statusCode: 401,
        type: 'suspicious',
        suspiciousType: SUSPICIOUS_PATTERNS.repeated_401,
      });
      const suspicious = getSuspiciousAccessLog();
      expect(suspicious.length).toBeGreaterThan(0);
      expect(suspicious.some(e => e.suspiciousType === SUSPICIOUS_PATTERNS.repeated_401)).toBe(true);
    });
  });
});
