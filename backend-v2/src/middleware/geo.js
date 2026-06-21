const geoip = require('geoip-lite');

/**
 * Resolves the real client IP, accounting for being behind a proxy (Render, etc.)
 * `app.set('trust proxy', 1)` must be set in server.js for req.ip to be accurate.
 */
function getClientIp(req) {
  const forwarded = req.headers['x-forwarded-for'];
  if (forwarded) return forwarded.split(',')[0].trim();
  return req.ip || req.socket?.remoteAddress || null;
}

/**
 * Attaches req.geo = { ip, country, region, city, timezone, currency } based on IP lookup.
 * Never blocks the request if lookup fails — defaults to null fields, frontend/backend
 * callers must handle that gracefully (e.g. fall back to a country picker).
 */
function detectCountry(req, res, next) {
  const ip = getClientIp(req);
  let geo = null;

  // geoip-lite can't resolve private/loopback IPs (localhost, LAN) — expected in dev
  if (ip && !isPrivateIp(ip)) {
    geo = geoip.lookup(ip);
  }

  req.geo = {
    ip,
    country: geo?.country || null, // ISO 3166-1 alpha-2
    region: geo?.region || null,
    city: geo?.city || null,
    timezone: geo?.timezone || null,
  };

  next();
}

function isPrivateIp(ip) {
  return (
    ip === '127.0.0.1' ||
    ip === '::1' ||
    ip.startsWith('10.') ||
    ip.startsWith('192.168.') ||
    /^172\.(1[6-9]|2\d|3[0-1])\./.test(ip)
  );
}

const COUNTRY_CURRENCY_MAP = {
  NG: 'NGN',
  GH: 'GHS',
  KE: 'KES',
  ZA: 'ZAR',
  US: 'USD',
  GB: 'GBP',
};

function currencyForCountry(countryCode) {
  return COUNTRY_CURRENCY_MAP[countryCode] || 'USD';
}

module.exports = { detectCountry, getClientIp, currencyForCountry };
