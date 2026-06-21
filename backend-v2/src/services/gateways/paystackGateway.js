const axios = require('axios');
const crypto = require('crypto');

/**
 * Paystack gateway adapter.
 * Flow: initialize transaction server-side -> redirect customer to authorization_url ->
 * confirm via webhook (charge.success) AND/OR server-side verify call before crediting wallet.
 */

function client() {
  const baseUrl = process.env.PAYSTACK_BASE_URL || 'https://api.paystack.co';
  const secretKey = process.env.PAYSTACK_SECRET_KEY;
  if (!secretKey) throw new Error('Paystack is not configured (missing PAYSTACK_SECRET_KEY)');
  return axios.create({
    baseURL: baseUrl,
    headers: { Authorization: `Bearer ${secretKey}`, 'Content-Type': 'application/json' },
  });
}

async function initializeTransaction({ amount, customerEmail, paymentReference, redirectUrl }) {
  const { data } = await client().post('/transaction/initialize', {
    email: customerEmail,
    amount: Math.round(amount * 100), // Paystack expects kobo (smallest unit)
    reference: paymentReference,
    callback_url: redirectUrl,
    currency: 'NGN',
  });

  if (!data.status) {
    throw new Error(`Paystack init failed: ${data.message}`);
  }

  return {
    checkoutUrl: data.data.authorization_url,
    transactionReference: data.data.reference,
    accessCode: data.data.access_code,
    raw: data.data,
  };
}

async function verifyTransaction(reference) {
  const { data } = await client().get(`/transaction/verify/${encodeURIComponent(reference)}`);
  if (!data.status) {
    throw new Error(`Paystack verify failed: ${data.message}`);
  }
  return data.data; // data.data.status: 'success' | 'failed' | 'abandoned'
}

/**
 * Paystack signs webhooks with HMAC SHA512 of the raw body, header 'x-paystack-signature'.
 */
function verifyWebhookSignature(rawBody, signatureHeader) {
  const secretKey = process.env.PAYSTACK_SECRET_KEY;
  if (!secretKey || !signatureHeader) return false;

  const computed = crypto.createHmac('sha512', secretKey).update(rawBody).digest('hex');
  try {
    return crypto.timingSafeEqual(Buffer.from(computed), Buffer.from(signatureHeader));
  } catch {
    return false;
  }
}

module.exports = { initializeTransaction, verifyTransaction, verifyWebhookSignature };
