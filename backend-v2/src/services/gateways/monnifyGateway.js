const axios = require('axios');

/**
 * Monnify gateway adapter.
 * Flow: get OAuth bearer token -> initialize transaction -> return hosted checkoutUrl.
 * We NEVER accept raw card numbers on our server. The customer enters card details
 * on Monnify's hosted checkout page, never on ours.
 */

let cachedToken = null;
let cachedTokenExpiresAt = 0;

async function getAccessToken() {
  if (cachedToken && Date.now() < cachedTokenExpiresAt - 30_000) {
    return cachedToken;
  }

  const baseUrl = process.env.MONNIFY_BASE_URL;
  const apiKey = process.env.MONNIFY_API_KEY;
  const secretKey = process.env.MONNIFY_SECRET_KEY;

  if (!baseUrl || !apiKey || !secretKey) {
    throw new Error('Monnify is not configured (missing MONNIFY_BASE_URL/API_KEY/SECRET_KEY)');
  }

  const basicAuth = Buffer.from(`${apiKey}:${secretKey}`).toString('base64');

  const { data } = await axios.post(
    `${baseUrl}/api/v1/auth/login`,
    {},
    { headers: { Authorization: `Basic ${basicAuth}` } }
  );

  if (!data.requestSuccessful) {
    throw new Error(`Monnify auth failed: ${data.responseMessage}`);
  }

  cachedToken = data.responseBody.accessToken;
  cachedTokenExpiresAt = Date.now() + data.responseBody.expiresIn * 1000;
  return cachedToken;
}

/**
 * @param {object} params
 * @param {number} params.amount
 * @param {string} params.customerEmail
 * @param {string} params.customerName
 * @param {string} params.paymentReference - our internal idempotency reference
 * @param {string} params.redirectUrl - where Monnify sends the user after payment
 * @returns {Promise<{checkoutUrl: string, transactionReference: string}>}
 */
async function initializeTransaction({ amount, customerEmail, customerName, paymentReference, redirectUrl }) {
  const baseUrl = process.env.MONNIFY_BASE_URL;
  const contractCode = process.env.MONNIFY_CONTRACT_CODE;
  const token = await getAccessToken();

  const { data } = await axios.post(
    `${baseUrl}/api/v1/merchant/transactions/init-transaction`,
    {
      amount,
      customerName,
      customerEmail,
      paymentReference,
      paymentDescription: 'Connexa wallet top-up',
      currencyCode: 'NGN',
      contractCode,
      redirectUrl,
      paymentMethods: ['CARD', 'ACCOUNT_TRANSFER'],
    },
    { headers: { Authorization: `Bearer ${token}` } }
  );

  if (!data.requestSuccessful) {
    throw new Error(`Monnify transaction init failed: ${data.responseMessage}`);
  }

  return {
    checkoutUrl: data.responseBody.checkoutUrl,
    transactionReference: data.responseBody.transactionReference,
    raw: data.responseBody,
  };
}

/**
 * Server-side verification — called from the webhook handler AND/OR the redirect callback,
 * never trust the client-reported status alone.
 */
async function verifyTransaction(transactionReference) {
  const baseUrl = process.env.MONNIFY_BASE_URL;
  const token = await getAccessToken();

  const { data } = await axios.get(
    `${baseUrl}/api/v2/transactions/${encodeURIComponent(transactionReference)}`,
    { headers: { Authorization: `Bearer ${token}` } }
  );

  if (!data.requestSuccessful) {
    throw new Error(`Monnify verify failed: ${data.responseMessage}`);
  }

  return data.responseBody; // includes paymentStatus: PAID | PENDING | FAILED | etc.
}

/**
 * Validates that an inbound webhook actually originated from Monnify.
 * Monnify signs webhook payloads using HMAC SHA512 of the raw body with your secret key,
 * sent in the 'monnify-signature' header. We re-derive the rawBody in middleware to support this.
 */
function verifyWebhookSignature(rawBody, signatureHeader) {
  const crypto = require('crypto');
  const secretKey = process.env.MONNIFY_SECRET_KEY;
  if (!secretKey || !signatureHeader) return false;

  const computed = crypto.createHmac('sha512', secretKey).update(rawBody).digest('hex');
  try {
    return crypto.timingSafeEqual(Buffer.from(computed), Buffer.from(signatureHeader));
  } catch {
    return false; // length mismatch etc. -> treat as invalid rather than throwing
  }
}

module.exports = { initializeTransaction, verifyTransaction, verifyWebhookSignature };
