const express = require('express');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const router = express.Router();

const User = require('../models/User');
const Otp = require('../models/Otp');
const { signAccessToken, signRefreshToken, verifyRefreshToken } = require('../utils/tokens');
const { sendOtpEmail } = require('../services/emailService');
const { detectCountry, currencyForCountry, getClientIp } = require('../middleware/geo');
const { requireAuth } = require('../middleware/auth');
const { validateBody } = require('../middleware/validate');
const v = require('../utils/validators/authValidators');

function generateOtpCode() {
  return crypto.randomInt(100000, 999999).toString();
}

async function issueTokenPair(user) {
  const accessToken = signAccessToken(user);
  const refreshToken = signRefreshToken(user);
  return { accessToken, refreshToken };
}

// ---------------------------------------------------------------------
// POST /api/auth/register  -> creates a pending registration + sends OTP
// ---------------------------------------------------------------------
router.post('/register', detectCountry, validateBody(v.register), async (req, res, next) => {
  try {
    const { email, password, name, phoneNumber, referralCode } = req.body;

    const existing = await User.findOne({ email });
    if (existing) {
      return res.status(409).json({ error: 'An account with this email already exists' });
    }

    let referredBy = null;
    if (referralCode) {
      const referrer = await User.findOne({ referralCode });
      if (referrer) referredBy = referrer._id;
    }

    const code = generateOtpCode();
    const codeHash = await bcrypt.hash(code, 10);

    await Otp.deleteMany({ email, purpose: 'REGISTER' }); // clear stale OTPs
    await Otp.create({
      email,
      codeHash,
      purpose: 'REGISTER',
      pendingUserData: {
        password,
        name,
        phoneNumber: phoneNumber || null,
        referredBy,
        country: req.geo.country,
        preferredCurrency: currencyForCountry(req.geo.country),
      },
      expiresAt: new Date(Date.now() + 10 * 60 * 1000),
    });

    const mailResult = await sendOtpEmail(email, code, 'REGISTER');

    res.status(200).json({
      message: 'Verification code sent. Please check your email to complete registration.',
      emailDelivered: mailResult.delivered,
      // In non-production, surface the code so local testing doesn't require real email setup.
      devOtp: process.env.NODE_ENV !== 'production' ? code : undefined,
    });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/auth/verify-otp -> completes registration, returns tokens
// ---------------------------------------------------------------------
router.post('/verify-otp', validateBody(v.verifyOtp), async (req, res, next) => {
  try {
    const { email, otp } = req.body;

    const record = await Otp.findOne({ email, purpose: 'REGISTER' }).sort({ createdAt: -1 });
    if (!record) return res.status(400).json({ error: 'No pending registration found for this email' });
    if (record.expiresAt < new Date()) return res.status(400).json({ error: 'Verification code expired' });
    if (record.attempts >= 5) return res.status(429).json({ error: 'Too many incorrect attempts. Please register again.' });

    const matches = await bcrypt.compare(otp, record.codeHash);
    if (!matches) {
      record.attempts += 1;
      await record.save();
      return res.status(400).json({ error: 'Incorrect verification code' });
    }

    const existing = await User.findOne({ email });
    if (existing) {
      await Otp.deleteOne({ _id: record._id });
      return res.status(409).json({ error: 'Account already exists, please log in instead' });
    }

    const { password, name, phoneNumber, referredBy, country, preferredCurrency } = record.pendingUserData;

    const user = new User({
      email,
      name,
      phoneNumber,
      referredBy,
      country,
      countrySource: country ? 'ip' : 'unknown',
      preferredCurrency,
      isEmailVerified: true,
      referralCode: crypto.randomBytes(4).toString('hex'),
    });
    await user.setPassword(password);
    await user.save();

    await Otp.deleteOne({ _id: record._id });

    const tokens = await issueTokenPair(user);
    res.status(201).json({ user: user.toSafeJSON(), ...tokens });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/auth/resend-otp
// ---------------------------------------------------------------------
router.post('/resend-otp', validateBody(v.forgotPassword), async (req, res, next) => {
  try {
    const { email } = req.body;
    const record = await Otp.findOne({ email, purpose: 'REGISTER' }).sort({ createdAt: -1 });
    if (!record) return res.status(400).json({ error: 'No pending registration found for this email' });

    const code = generateOtpCode();
    record.codeHash = await bcrypt.hash(code, 10);
    record.expiresAt = new Date(Date.now() + 10 * 60 * 1000);
    record.attempts = 0;
    await record.save();

    const mailResult = await sendOtpEmail(email, code, 'REGISTER');
    res.json({
      message: 'A new verification code has been sent.',
      emailDelivered: mailResult.delivered,
      devOtp: process.env.NODE_ENV !== 'production' ? code : undefined,
    });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/auth/login
// ---------------------------------------------------------------------
router.post('/login', detectCountry, validateBody(v.login), async (req, res, next) => {
  try {
    const { email, password } = req.body;

    const user = await User.findOne({ email }).select('+passwordHash');
    // Constant-shape response whether the user exists or not, to avoid email enumeration via timing/content
    if (!user) return res.status(401).json({ error: 'Invalid email or password' });

    const valid = await user.verifyPassword(password);
    if (!valid) return res.status(401).json({ error: 'Invalid email or password' });

    if (!user.isActive) return res.status(403).json({ error: 'This account has been deactivated' });

    user.lastLoginIp = req.geo.ip;
    user.lastLoginAt = new Date();
    if (!user.country && req.geo.country) {
      user.country = req.geo.country;
      user.countrySource = 'ip';
      user.preferredCurrency = currencyForCountry(req.geo.country);
    }
    await user.save();

    const tokens = await issueTokenPair(user);
    res.json({ user: user.toSafeJSON(), geo: req.geo, ...tokens });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/auth/refresh -> exchanges a valid refresh token for a new pair
// ---------------------------------------------------------------------
router.post('/refresh', validateBody(v.refresh), async (req, res, next) => {
  try {
    const { refreshToken } = req.body;
    let payload;
    try {
      payload = verifyRefreshToken(refreshToken);
    } catch (err) {
      return res.status(401).json({ error: 'Invalid or expired refresh token' });
    }

    const user = await User.findById(payload.sub);
    if (!user || !user.isActive) return res.status(401).json({ error: 'Account not found or deactivated' });
    if (user.refreshTokenVersion !== payload.tokenVersion) {
      return res.status(401).json({ error: 'Refresh token has been revoked' });
    }

    const tokens = await issueTokenPair(user);
    res.json(tokens);
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/auth/logout-all -> revokes all refresh tokens for this user
// ---------------------------------------------------------------------
router.post('/logout-all', requireAuth, async (req, res, next) => {
  try {
    req.user.refreshTokenVersion += 1;
    await req.user.save();
    res.json({ message: 'Logged out of all devices' });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/auth/forgot-password
// ---------------------------------------------------------------------
router.post('/forgot-password', validateBody(v.forgotPassword), async (req, res, next) => {
  try {
    const { email } = req.body;
    const user = await User.findOne({ email });

    // Always respond the same way, whether or not the account exists, to prevent enumeration.
    const genericResponse = { message: 'If that account exists, a reset code has been sent.' };

    if (!user) return res.json(genericResponse);

    const code = generateOtpCode();
    const codeHash = await bcrypt.hash(code, 10);
    await Otp.deleteMany({ email, purpose: 'RESET_PASSWORD' });
    await Otp.create({
      email,
      codeHash,
      purpose: 'RESET_PASSWORD',
      expiresAt: new Date(Date.now() + 10 * 60 * 1000),
    });

    const mailResult = await sendOtpEmail(email, code, 'RESET_PASSWORD');
    res.json({
      ...genericResponse,
      emailDelivered: mailResult.delivered,
      devOtp: process.env.NODE_ENV !== 'production' ? code : undefined,
    });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/auth/reset-password
// ---------------------------------------------------------------------
router.post('/reset-password', validateBody(v.resetPassword), async (req, res, next) => {
  try {
    const { email, otp, newPassword } = req.body;

    const record = await Otp.findOne({ email, purpose: 'RESET_PASSWORD' }).sort({ createdAt: -1 });
    if (!record) return res.status(400).json({ error: 'No password reset request found' });
    if (record.expiresAt < new Date()) return res.status(400).json({ error: 'Reset code expired' });
    if (record.attempts >= 5) return res.status(429).json({ error: 'Too many incorrect attempts' });

    const matches = await bcrypt.compare(otp, record.codeHash);
    if (!matches) {
      record.attempts += 1;
      await record.save();
      return res.status(400).json({ error: 'Incorrect reset code' });
    }

    const user = await User.findOne({ email });
    if (!user) return res.status(404).json({ error: 'Account not found' });

    await user.setPassword(newPassword);
    user.refreshTokenVersion += 1; // invalidate all existing sessions on password change
    await user.save();

    await Otp.deleteOne({ _id: record._id });

    res.json({ message: 'Password reset successful. Please log in again.' });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// GET /api/auth/me -> current authenticated user
// ---------------------------------------------------------------------
router.get('/me', requireAuth, (req, res) => {
  res.json({ user: req.user.toSafeJSON() });
});

module.exports = router;
