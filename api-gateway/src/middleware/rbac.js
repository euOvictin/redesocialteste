/**
 * Role-Based Access Control (RBAC)
 * Requirement: 14.5 - user, moderator, admin roles
 */
const logger = require('../utils/logger');

const ROLES = {
  USER: 'user',
  MODERATOR: 'moderator',
  ADMIN: 'admin',
};

/**
 * Middleware to require specific role(s)
 * req.user must have role property (from JWT or DB)
 * For now, default role is 'user' - extend JWT to include role
 */
function requireRole(...allowedRoles) {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({
        error: { code: 'UNAUTHORIZED', message: 'Autenticação necessária' },
      });
    }
    const userRole = req.user.role || ROLES.USER;
    if (!allowedRoles.includes(userRole)) {
      logger.warn({ message: 'Forbidden: role insufficient', userId: req.user.userId, role: userRole });
      return res.status(403).json({
        error: {
          code: 'FORBIDDEN',
          message: 'Acesso restrito. Permissões insuficientes.',
        },
      });
    }
    next();
  };
}

module.exports = { requireRole, ROLES };
