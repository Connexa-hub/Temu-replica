const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const userSchema = new mongoose.Schema(
  {
    email: { type: String, required: true, unique: true, lowercase: true, trim: true, index: true },
    passwordHash: { type: String, required: true, select: false },
    name: { type: String, required: true, trim: true },
    phoneNumber: { type: String, trim: true },

    role: { type: String, enum: ['user', 'admin', 'superadmin'], default: 'user', index: true },

    // Country / locale — set from IP geolocation at registration & refreshed at login
    country: { type: String, default: null }, // ISO 3166-1 alpha-2, e.g. "NG"
    countrySource: { type: String, enum: ['ip', 'manual', 'unknown'], default: 'unknown' },
    preferredCurrency: { type: String, default: 'NGN' },
    lastLoginIp: { type: String, default: null },
    lastLoginAt: { type: Date, default: null },

    isEmailVerified: { type: Boolean, default: false },
    isActive: { type: Boolean, default: true },

    // UI preference, synced across devices
    themeMode: { type: String, enum: ['light', 'dark', 'system'], default: 'system' },

    referredBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null },
    referralCode: { type: String, unique: true, sparse: true },

    refreshTokenVersion: { type: Number, default: 0 }, // bump to invalidate all refresh tokens
  },
  { timestamps: true }
);

userSchema.methods.setPassword = async function setPassword(plainPassword) {
  const saltRounds = 12;
  this.passwordHash = await bcrypt.hash(plainPassword, saltRounds);
};

userSchema.methods.verifyPassword = async function verifyPassword(plainPassword) {
  if (!this.passwordHash) return false;
  return bcrypt.compare(plainPassword, this.passwordHash);
};

userSchema.methods.toSafeJSON = function toSafeJSON() {
  return {
    id: this._id,
    email: this.email,
    name: this.name,
    phoneNumber: this.phoneNumber,
    role: this.role,
    country: this.country,
    preferredCurrency: this.preferredCurrency,
    isEmailVerified: this.isEmailVerified,
    themeMode: this.themeMode,
    referralCode: this.referralCode,
    createdAt: this.createdAt,
  };
};

module.exports = mongoose.model('User', userSchema);
