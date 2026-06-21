const mongoose = require('mongoose');
const Wallet = require('../models/Wallet');
const WalletTransaction = require('../models/WalletTransaction');

class WalletError extends Error {
  constructor(message, code = 'WALLET_ERROR') {
    super(message);
    this.code = code;
  }
}

async function getOrCreateWallet(userId, session = null) {
  let wallet = await Wallet.findOne({ user: userId }).session(session);
  if (!wallet) {
    const created = await Wallet.create([{ user: userId, balance: 0 }], { session });
    wallet = created[0];
  }
  return wallet;
}

/**
 * Creates a PENDING ledger entry before we call out to a payment gateway.
 * This reserves the internalReference so retries / duplicate webhook calls are safely ignored.
 */
async function createPendingDeposit({ userId, amount, currency, gateway, internalReference, description }) {
  if (!(amount > 0)) throw new WalletError('Amount must be positive', 'INVALID_AMOUNT');

  const wallet = await getOrCreateWallet(userId);

  const existing = await WalletTransaction.findOne({ internalReference });
  if (existing) return existing; // idempotent: caller retried with the same reference

  const tx = await WalletTransaction.create({
    wallet: wallet._id,
    user: userId,
    type: 'DEPOSIT',
    amount,
    currency: currency || wallet.currency,
    balanceAfter: wallet.balance, // unchanged until confirmed
    status: 'PENDING',
    gateway,
    internalReference,
    description: description || `Wallet top-up via ${gateway}`,
  });

  return tx;
}

/**
 * Confirms a deposit after gateway webhook/verify confirms success.
 * Idempotent: calling this twice for the same reference will only credit once.
 */
async function confirmDeposit({ internalReference, gatewayReference, gatewayPayloadSnapshot }) {
  const session = await mongoose.startSession();
  try {
    let result;
    await session.withTransaction(async () => {
      const tx = await WalletTransaction.findOne({ internalReference }).session(session);
      if (!tx) throw new WalletError(`No pending transaction for reference ${internalReference}`, 'TX_NOT_FOUND');

      if (tx.status === 'SUCCESS') {
        result = tx; // already processed — idempotent no-op
        return;
      }
      if (tx.status !== 'PENDING') {
        throw new WalletError(`Transaction ${internalReference} is in terminal state ${tx.status}`, 'INVALID_STATE');
      }

      const wallet = await Wallet.findById(tx.wallet).session(session);
      if (!wallet) throw new WalletError('Wallet not found for transaction', 'WALLET_NOT_FOUND');
      if (wallet.isFrozen) throw new WalletError('Wallet is frozen, cannot credit', 'WALLET_FROZEN');

      wallet.balance = Math.round((wallet.balance + tx.amount) * 100) / 100;
      await wallet.save({ session });

      tx.status = 'SUCCESS';
      tx.gatewayReference = gatewayReference;
      tx.gatewayPayloadSnapshot = gatewayPayloadSnapshot;
      tx.balanceAfter = wallet.balance;
      await tx.save({ session });

      result = tx;
    });
    return result;
  } finally {
    session.endSession();
  }
}

async function markDepositFailed({ internalReference, gatewayPayloadSnapshot }) {
  const tx = await WalletTransaction.findOne({ internalReference });
  if (!tx) return null;
  if (tx.status !== 'PENDING') return tx; // don't overwrite a terminal state
  tx.status = 'FAILED';
  tx.gatewayPayloadSnapshot = gatewayPayloadSnapshot;
  await tx.save();
  return tx;
}

/**
 * Debits a wallet for an order payment. Atomic check-and-debit to prevent overspending.
 */
async function debitForOrder({ userId, amount, orderId, internalReference, description }) {
  if (!(amount > 0)) throw new WalletError('Amount must be positive', 'INVALID_AMOUNT');

  const session = await mongoose.startSession();
  try {
    let result;
    await session.withTransaction(async () => {
      const wallet = await getOrCreateWallet(userId, session);
      if (wallet.isFrozen) throw new WalletError('Wallet is frozen', 'WALLET_FROZEN');
      if (wallet.balance < amount) throw new WalletError('Insufficient wallet balance', 'INSUFFICIENT_FUNDS');

      wallet.balance = Math.round((wallet.balance - amount) * 100) / 100;
      await wallet.save({ session });

      const created = await WalletTransaction.create(
        [
          {
            wallet: wallet._id,
            user: userId,
            type: 'ORDER_PAYMENT',
            amount,
            currency: wallet.currency,
            balanceAfter: wallet.balance,
            status: 'SUCCESS',
            internalReference,
            relatedOrder: orderId,
            description: description || 'Order payment from wallet',
          },
        ],
        { session }
      );
      result = created[0];
    });
    return result;
  } finally {
    session.endSession();
  }
}

async function adminAdjustBalance({ userId, amount, adminUserId, reason }) {
  if (!amount || amount === 0) throw new WalletError('Adjustment amount must be non-zero', 'INVALID_AMOUNT');

  const session = await mongoose.startSession();
  try {
    let result;
    await session.withTransaction(async () => {
      const wallet = await getOrCreateWallet(userId, session);
      const newBalance = Math.round((wallet.balance + amount) * 100) / 100;
      if (newBalance < 0) throw new WalletError('Adjustment would result in negative balance', 'INVALID_ADJUSTMENT');

      wallet.balance = newBalance;
      await wallet.save({ session });

      const created = await WalletTransaction.create(
        [
          {
            wallet: wallet._id,
            user: userId,
            type: 'ADMIN_ADJUSTMENT',
            amount: Math.abs(amount),
            currency: wallet.currency,
            balanceAfter: wallet.balance,
            status: 'SUCCESS',
            internalReference: `admin-adj-${Date.now()}-${userId}`,
            description: reason || 'Manual admin adjustment',
            initiatedByAdmin: adminUserId,
          },
        ],
        { session }
      );
      result = created[0];
    });
    return result;
  } finally {
    session.endSession();
  }
}

module.exports = {
  WalletError,
  getOrCreateWallet,
  createPendingDeposit,
  confirmDeposit,
  markDepositFailed,
  debitForOrder,
  adminAdjustBalance,
};
