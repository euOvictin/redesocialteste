/**
 * Property 52: Entradas são sanitizadas
 * Validates: Requirements 10.4
 */
const fc = require('fast-check');
const { sanitizationMiddleware, containsMaliciousContent, sanitizeValue } = require('./sanitization');

describe('Sanitization - Property-Based Tests', () => {
  describe('Property 52: Entradas são sanitizadas', () => {
    it('should detect SQL injection patterns', () => {
      fc.assert(
        fc.property(
          fc.constantFrom("'; DROP TABLE users;", "1 OR 1=1--", "UNION SELECT * FROM users", "%27; DROP--"),
          (input) => {
            const result = containsMaliciousContent(input);
            expect(result).toBeTruthy();
            expect(result.type).toBeDefined();
          }
        ),
        { numRuns: 10 }
      );
    });

    it('should detect XSS patterns', () => {
      fc.assert(
        fc.property(
          fc.constantFrom("<script>alert(1)</script>", "javascript:alert(1)", "onerror=alert(1)", "<img onerror=alert(1)>"),
          (input) => {
            const result = containsMaliciousContent(input);
            expect(result).toBeTruthy();
          }
        ),
        { numRuns: 10 }
      );
    });

    it('should not flag safe strings', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 1, maxLength: 200 }).filter(s => 
            !s.includes('<script') && 
            !s.includes('javascript:') && 
            !s.includes('eval(') &&
            !s.includes('; DROP TABLE')
          ),
          (input) => {
            const result = containsMaliciousContent(input);
            expect(result).toBeFalsy();
          }
        ),
        { numRuns: 50 }
      );
    });

    it('sanitizeValue escapes HTML entities', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 1, maxLength: 100 }),
          (input) => {
            const sanitized = sanitizeValue(input);
            expect(sanitized).not.toMatch(/</);
            expect(sanitized).not.toMatch(/>/);
            expect(typeof sanitized).toBe('string');
          }
        ),
        { numRuns: 30 }
      );
    });
  });
});
