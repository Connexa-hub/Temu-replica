const express = require('express');
const router = express.Router();
const Joi = require('joi');

const { requireAuth } = require('../middleware/auth');
const { validateBody } = require('../middleware/validate');
const paymentService = require('../services/paymentService');
const walletService = require('../services/walletService');
const monnifyGateway = require('../services/gateways/monnifyGateway');
const paystackGateway = require('../services/gateways/paystackGateway');
const flutterwaveGateway = require('../services/gateways/flutterwaveGateway');
const WalletTransaction = require('../models/WalletTransaction');

// ---------------------------------------------------------------------
// POST /api/payments/deposit/start  (auth required)
// Body: { amount, gateway: 'MONNIFY' | 'PAYSTACK' | 'FLUTTERWAVE' }
// ---------------------------------------------------------------------
const startDepositSchema = Joi.object({
  amount: Joi.number().positive().required(),
  gateway: Joi.string().valid('MONNIFY', 'PAYSTACK', 'FLUTTERWAVE').required(),
});

router.post('/deposit/start', requireAuth, validateBody(startDepositSchema), async (req, res, next) => {
  try {
    const { amount, gateway } = req.body;
    const redirectUrl = `${process.env.CLIENT_BASE_URL}/wallet/deposit/callback`;

    const result = await paymentService.startDeposit({ user: req.user, amount, gateway, redirectUrl });
    res.json({
      message: 'Redirect the user to checkoutUrl to complete payment',
      ...result,
    });
  } catch (err) {
    if (err.code) return res.status(400).json({ error: err.message, code: err.code });
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/payments/deposit/verify  (auth required) - fallback polling endpoint
// Body: { internalReference, flutterwaveTransactionId? }
// ---------------------------------------------------------------------
const verifyDepositSchema = Joi.object({
  internalReference: Joi.string().required(),
  flutterwaveTransactionId: Joi.string().optional(),
});

router.post('/deposit/verify', requireAuth, validateBody(verifyDepositSchema), async (req, res, next) => {
  try {
    const { internalReference, flutterwaveTransactionId } = req.body;

    const tx = await WalletTransaction.findOne({ internalReference });
    if (!tx) return res.status(404).json({ error: 'Transaction not found' });
    if (String(tx.user) !== String(req.user._id)) {
      return res.status(403).json({ error: 'This transaction does not belong to you' });
    }

    const confirmed = await paymentService.verifyAndConfirmDeposit({ internalReference, flutterwaveTransactionId });
    res.json({ status: confirmed.status, transaction: confirmed });
  } catch (err) {
    if (err.code) return res.status(400).json({ error: err.message, code: err.code });
    next(err);
  }
});

// ---------------------------------------------------------------------
// GET /api/payments/wallet  (auth required) - balance + recent ledger
// ---------------------------------------------------------------------
router.get('/wallet', requireAuth, async (req, res, next) => {
  try {
    const wallet = await walletService.getOrCreateWallet(req.user._id);
    const recentTransactions = await WalletTransaction.find({ user: req.user._id })
      .sort({ createdAt: -1 })
      .limit(50);

    res.json({ wallet, recentTransactions });
  } catch (err) {
    next(err);
  }
});

// =======================================================================
// WEBHOOKS — these must receive the RAW body for signature verification.
// They are mounted in server.js with express.raw() BEFORE express.json()
// is applied globally, scoped only to these three paths.
// =======================================================================

router.post('/webhooks/monnify', async (req, res) => {
  try {
    const signature = req.headers['monnify-signature'];
    const rawBody = req.rawBody; // populated by the raw-body capture middleware in server.js
    if (!monnifyGateway.verifyWebhookSignature(rawBody, signature)) {
      console.warn('[WEBHOOK][MONNIFY] Invalid signature, rejecting');
      return res.status(401).json({ error: 'Invalid signature' });
    }

    const event = JSON.parse(rawBody.toString());
    const paymentReference = event?.eventData?.paymentReference;
    const paymentStatus = event?.eventData?.paymentStatus;

    if (event.eventType === 'SUCCESSFUL_TRANSACTION' && paymentStatus === 'PAID' && paymentReference) {
      await paymentService.verifyAndConfirmDeposit({ internalReference: paymentReference });
    }

    res.status(200).json({ received: true });
  } catch (err) {
    console.error('[WEBHOOK][MONNIFY] Processing error:', err.message);
    // Still acknowledge receipt so Monnify doesn't hammer retries for a bug on our side that
    // a human needs to look at — but the error is logged for investigation.
    res.status(200).json({ received: true, processed: false });
  }
});

router.post('/webhooks/paystack', async (req, res) => {
  try {
    const signature = req.headers['x-paystack-signature'];
    const rawBody = req.rawBody;
    if (!paystackGateway.verifyWebhookSignature(rawBody, signature)) {
      console.warn('[WEBHOOK][PAYSTACK] Invalid signature, rejecting');
      return res.status(401).json({ error: 'Invalid signature' });
    }

    const event = JSON.parse(rawBody.toString());
    if (event.event === 'charge.success') {
      const reference = event.data.reference;
      await paymentService.verifyAndConfirmDeposit({ internalReference: reference });
    }

    res.status(200).json({ received: true });
  } catch (err) {
    console.error('[WEBHOOK][PAYSTACK] Processing error:', err.message);
    res.status(200).json({ received: true, processed: false });
  }
});

router.post('/webhooks/flutterwave', async (req, res) => {
  try {
    const signature = req.headers['verif-hash'];
    if (!flutterwaveGateway.verifyWebhookSignature(signature)) {
      console.warn('[WEBHOOK][FLUTTERWAVE] Invalid signature, rejecting');
      return res.status(401).json({ error: 'Invalid signature' });
    }

    const event = JSON.parse(req.rawBody.toString());
    if (event.event === 'charge.completed' && event.data?.status === 'successful') {
      await paymentService.verifyAndConfirmDeposit({
        internalReference: event.data.tx_ref,
        flutterwaveTransactionId: event.data.id,
      });
    }

    res.status(200).json({ received: true });
  } catch (err) {
    console.error('[WEBHOOK][FLUTTERWAVE] Processing error:', err.message);
    res.status(200).json({ received: true, processed: false });
  }
});

module.exports = router;
