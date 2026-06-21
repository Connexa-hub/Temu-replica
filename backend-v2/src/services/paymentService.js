const crypto = require('crypto');
const monnify = require('./gateways/monnifyGateway');
const paystack = require('./gateways/paystackGateway');
const flutterwave = require('./gateways/flutterwaveGateway');
const walletService = require('./walletService');
const WalletTransaction = require('../models/WalletTransaction');

const SUPPORTED_GATEWAYS = ['MONNIFY', 'PAYSTACK', 'FLUTTERWAVE'];

function generateInternalReference(gateway) {
  return `${gateway.toLowerCase()}-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;
}

/**
 * Step 1 of a deposit: create a PENDING wallet ledger entry, then ask the chosen
 * gateway for a hosted checkout URL. Nothing is credited yet.
 */
async function startDeposit({ user, amount, gateway, redirectUrl }) {
  if (!SUPPORTED_GATEWAYS.includes(gateway)) {
    const err = new Error(`Unsupported gateway "${gateway}". Supported: ${SUPPORTED_GATEWAYS.join(', ')}`);
    err.code = 'UNSUPPORTED_GATEWAY';
    throw err;
  }
  if (!(amount > 0)) {
    const err = new Error('Amount must be a positive number');
    err.code = 'INVALID_AMOUNT';
    throw err;
  }

  const internalReference = generateInternalReference(gateway);

  await walletService.createPendingDeposit({
    userId: user._id,
    amount,
    currency: 'NGN',
    gateway,
    internalReference,
    description: `Wallet top-up via ${gateway}`,
  });

  let checkoutUrl;
  try {
    if (gateway === 'MONNIFY') {
      const result = await monnify.initializeTransaction({
        amount,
        customerEmail: user.email,
        customerName: user.name,
        paymentReference: internalReference,
        redirectUrl,
      });
      checkoutUrl = result.checkoutUrl;
    } else if (gateway === 'PAYSTACK') {
      const result = await paystack.initializeTransaction({
        amount,
        customerEmail: user.email,
        paymentReference: internalReference,
        redirectUrl,
      });
      checkoutUrl = result.checkoutUrl;
    } else if (gateway === 'FLUTTERWAVE') {
      const result = await flutterwave.initializeTransaction({
        amount,
        customerEmail: user.email,
        customerName: user.name,
        paymentReference: internalReference,
        redirectUrl,
      });
      checkoutUrl = result.checkoutUrl;
    }
  } catch (err) {
    await walletService.markDepositFailed({
      internalReference,
      gatewayPayloadSnapshot: { initError: err.message },
    });
    throw err;
  }

  return { internalReference, checkoutUrl, gateway };
}

/**
 * Server-side re-verification, used both by webhook handlers (defense in depth) and by
 * an explicit "check my payment status" endpoint the frontend can poll as a fallback.
 */
async function verifyAndConfirmDeposit({ internalReference, flutterwaveTransactionId }) {
  const tx = await WalletTransaction.findOne({ internalReference });
  if (!tx) {
    const err = new Error('Unknown transaction reference');
    err.code = 'TX_NOT_FOUND';
    throw err;
  }
  if (tx.status === 'SUCCESS') return tx; // idempotent

  let isPaid = false;
  let gatewayReference = null;
  let snapshot = null;

  try {
    if (tx.gateway === 'MONNIFY') {
      const result = await monnify.verifyTransaction(internalReference);
      isPaid = result.paymentStatus === 'PAID';
      gatewayReference = result.transactionReference;
      snapshot = result;
    } else if (tx.gateway === 'PAYSTACK') {
      const result = await paystack.verifyTransaction(internalReference);
      isPaid = result.status === 'success' && Math.round(result.amount / 100) === Math.round(tx.amount);
      gatewayReference = result.reference;
      snapshot = result;
    } else if (tx.gateway === 'FLUTTERWAVE') {
      if (!flutterwaveTransactionId) {
        const err = new Error('flutterwaveTransactionId is required to verify a Flutterwave payment');
        err.code = 'MISSING_FLW_ID';
        throw err;
      }
      const result = await flutterwave.verifyTransactionById(flutterwaveTransactionId);
      isPaid =
        result.status === 'successful' &&
        result.tx_ref === internalReference &&
        Math.round(result.amount) === Math.round(tx.amount) &&
        result.currency === tx.currency;
      gatewayReference = String(result.id);
      snapshot = result;
    }
  } catch (verifyErr) {
    // The gateway call itself blew up (network blip, gateway downtime, etc).
    // This is NOT the same as the gateway telling us the payment failed — leave the
    // transaction PENDING so it can be retried, rather than wrongly marking it FAILED.
    if (verifyErr.code === 'MISSING_FLW_ID') throw verifyErr;
    console.error(`[PAYMENT VERIFY] Transient error verifying ${internalReference}:`, verifyErr.message);
    const err = new Error('Could not reach payment gateway to verify status. Please retry shortly.');
    err.code = 'VERIFY_UNAVAILABLE';
    throw err;
  }

  if (isPaid) {
    return walletService.confirmDeposit({
      internalReference,
      gatewayReference,
      gatewayPayloadSnapshot: snapshot,
    });
  }

  // Gateway explicitly confirmed this payment did NOT succeed — safe to mark FAILED.
  await walletService.markDepositFailed({ internalReference, gatewayPayloadSnapshot: snapshot });
  const err = new Error('Payment was not successful');
  err.code = 'PAYMENT_NOT_SUCCESSFUL';
  throw err;
}

module.exports = { SUPPORTED_GATEWAYS, startDeposit, verifyAndConfirmDeposit };
