const { verifyAccessToken } = require('../utils/tokens');
const User = require('../models/User');

/**
 * Requires a valid, non-expired access token. Attaches req.user (lean, from DB — not just the token claims)
 * so role/active-status changes take effect immediately rather than waiting for token expiry.
 */
async function requireAuth(req, res, next) {
  try {
    const header = req.headers.authorization || '';
    const [scheme, token] = header.split(' ');
    if (scheme !== 'Bearer' || !token) {
      return res.status(401).json({ error: 'Missing or malformed Authorization header. Expected: Bearer <token>' });
    }

    let payload;
    try {
      payload = verifyAccessToken(token);
    } catch (err) {
      const reason = err.name === 'TokenExpiredError' ? 'Access token expired' : 'Invalid access token';
      return res.status(401).json({ error: reason, code: err.name });
    }

    const user = await User.findById(payload.sub);
    if (!user || !user.isActive) {
      return res.status(401).json({ error: 'Account not found or deactivated' });
    }

    req.user = user;
    next();
  } catch (err) {
    next(err);
  }
}

function requireRole(...allowedRoles) {
  return (req, res, next) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    if (!allowedRoles.includes(req.user.role)) {
      return res.status(403).json({ error: `Forbidden: requires one of roles [${allowedRoles.join(', ')}]` });
    }
    next();
  };
}

// Attaches req.user if a valid token is present, but does not block the request otherwise.
async function optionalAuth(req, res, next) {
  try {
    const header = req.headers.authorization || '';
    const [scheme, token] = header.split(' ');
    if (scheme !== 'Bearer' || !token) return next();

    const payload = verifyAccessToken(token);
    const user = await User.findById(payload.sub);
    if (user && user.isActive) req.user = user;
    next();
  } catch {
    next(); // invalid/expired token on an optional route — proceed unauthenticated
  }
}

module.exports = { requireAuth, requireRole, optionalAuth };
