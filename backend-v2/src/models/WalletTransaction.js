const mongoose = require('mongoose');

const walletTransactionSchema = new mongoose.Schema(
  {
    wallet: { type: mongoose.Schema.Types.ObjectId, ref: 'Wallet', required: true, index: true },
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },

    type: {
      type: String,
      enum: ['DEPOSIT', 'WITHDRAWAL', 'ORDER_PAYMENT', 'REFUND', 'REFERRAL_BONUS', 'ADMIN_ADJUSTMENT'],
      required: true,
    },

    amount: { type: Number, required: true }, // always positive; sign of effect determined by `type`
    currency: { type: String, default: 'NGN' },

    balanceAfter: { type: Number, required: true }, // snapshot for audit trail

    status: {
      type: String,
      enum: ['PENDING', 'SUCCESS', 'FAILED', 'REVERSED'],
      default: 'PENDING',
      index: true,
    },

    // Which payment gateway funded this (null for non-deposit types)
    gateway: { type: String, enum: ['MONNIFY', 'PAYSTACK', 'FLUTTERWAVE', 'STRIPE', null], default: null },

    // Our internal idempotency key, generated before calling the gateway
    internalReference: { type: String, required: true, unique: true, index: true },

    // The gateway's own transaction reference, set once confirmed via webhook/verify
    gatewayReference: { type: String, default: null, index: true },

    // Raw gateway payload kept for audit/dispute resolution — never trusted blindly, only stored
    gatewayPayloadSnapshot: { type: mongoose.Schema.Types.Mixed, default: null },

    relatedOrder: { type: mongoose.Schema.Types.ObjectId, ref: 'Order', default: null },

    description: { type: String, default: '' },

    initiatedByAdmin: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null }, // for ADMIN_ADJUSTMENT audit
  },
  { timestamps: true }
);

walletTransactionSchema.index({ user: 1, createdAt: -1 });

module.exports = mongoose.model('WalletTransaction', walletTransactionSchema);
