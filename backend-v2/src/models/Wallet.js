const mongoose = require('mongoose');

const walletSchema = new mongoose.Schema(
  {
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, unique: true, index: true },
    balance: { type: Number, required: true, default: 0, min: 0 }, // kept in sync via WalletTransaction entries only
    currency: { type: String, default: 'NGN' },
    isFrozen: { type: Boolean, default: false },
    frozenReason: { type: String, default: null },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Wallet', walletSchema);
