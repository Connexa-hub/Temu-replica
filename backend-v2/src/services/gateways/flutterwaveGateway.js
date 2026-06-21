const axios = require('axios');
const crypto = require('crypto');

/**
 * Flutterwave v3 gateway adapter.
 * Flow: initialize payment -> redirect to hosted payment link -> verify server-side by transaction id
 * before crediting wallet. Webhook auth uses a static secret hash compared against 'verif-hash'.
 */

function client() {
  const baseUrl = process.env.FLUTTERWAVE_BASE_URL || 'https://api.flutterwave.com/v3';
  const secretKey = process.env.FLUTTERWAVE_SECRET_KEY;
  if (!secretKey) throw new Error('Flutterwave is not configured (missing FLUTTERWAVE_SECRET_KEY)');
  return axios.create({
    baseURL: baseUrl,
    headers: { Authorization: `Bearer ${secretKey}`, 'Content-Type': 'application/json' },
  });
}

async function initializeTransaction({ amount, customerEmail, customerName, paymentReference, redirectUrl }) {
  const { data } = await client().post('/payments', {
    tx_ref: paymentReference,
    amount,
    currency: 'NGN',
    redirect_url: redirectUrl,
    customer: { email: customerEmail, name: customerName },
    customizations: { title: 'Connexa Wallet Top-up' },
  });

  if (data.status !== 'success') {
    throw new Error(`Flutterwave init failed: ${data.message}`);
  }

  return {
    checkoutUrl: data.data.link,
    transactionReference: paymentReference, // FLW also assigns its own numeric `id` at verify time
    raw: data.data,
  };
}

/**
 * Flutterwave's verify endpoint takes their internal numeric transaction id (returned in the
 * redirect query string as `transaction_id`, and in the webhook payload as `data.id`),
 * not our tx_ref. Always verify by id, then cross-check tx_ref + amount + currency ourselves.
 */
async function verifyTransactionById(transactionId) {
  const { data } = await client().get(`/transactions/${encodeURIComponent(transactionId)}/verify`);
  if (data.status !== 'success') {
    throw new Error(`Flutterwave verify failed: ${data.message}`);
  }
  return data.data; // data.data.status: 'successful' | 'failed', data.data.tx_ref, data.data.amount, data.data.currency
}

/**
 * Flutterwave does NOT use HMAC signing. The dashboard-configured secret hash is sent verbatim
 * in the 'verif-hash' header, and we just compare it directly to our stored value.
 * This is a SEPARATE secret from FLUTTERWAVE_ENCRYPTION_KEY (which is unrelated, used for
 * the old card-charge encryption flow) — never conflate the two.
 */
function verifyWebhookSignature(signatureHeader) {
  const secretHash = process.env.FLUTTERWAVE_SECRET_HASH;
  if (!secretHash || !signatureHeader) return false;
  try {
    const a = Buffer.from(signatureHeader);
    const b = Buffer.from(secretHash);
    if (a.length !== b.length) return false; // timingSafeEqual requires equal length buffers
    return crypto.timingSafeEqual(a, b);
  } catch {
    return false;
  }
}

module.exports = { initializeTransaction, verifyTransactionById, verifyWebhookSignature };
