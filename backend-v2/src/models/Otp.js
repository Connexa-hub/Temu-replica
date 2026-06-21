const mongoose = require('mongoose');

const otpSchema = new mongoose.Schema(
  {
    email: { type: String, required: true, lowercase: true, index: true },
    codeHash: { type: String, required: true }, // OTP stored hashed, never plaintext
    purpose: { type: String, enum: ['REGISTER', 'RESET_PASSWORD'], required: true },
    pendingUserData: { type: mongoose.Schema.Types.Mixed, default: null }, // holds registration payload until verified
    expiresAt: { type: Date, required: true, index: { expires: 0 } }, // TTL index — Mongo auto-deletes expired docs
    attempts: { type: Number, default: 0 },
    consumedAt: { type: Date, default: null },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Otp', otpSchema);
