/**
 * Card brand detection from a BIN (Bank Identification Number — the first 6-8 digits).
 *
 * IMPORTANT SCOPE NOTE: this module NEVER receives, stores, or transmits a full card number.
 * It only ever sees the BIN prefix, which is used purely to show the customer a recognizable
 * "Visa / Mastercard / Verve" card face (like Stripe/Paystack's checkout UI does) in OUR
 * deposit-flow UI, before redirecting to the gateway's own PCI-compliant hosted checkout where
 * the actual card number is entered. We deliberately do not implement full BIN-to-issuing-bank
 * lookups (that requires either a paid BIN database or sending PAN data to a third party,
 * neither of which belongs in a wallet-topup preview step).
 */

const BRAND_PATTERNS = [
  { brand: 'VISA', pattern: /^4/, gradient: ['#1A1F71', '#0F4C95'], logo: 'visa' },
  { brand: 'MASTERCARD', pattern: /^(5[1-5]|2[2-7])/, gradient: ['#EB001B', '#F79E1B'], logo: 'mastercard' },
  { brand: 'VERVE', pattern: /^(506[01]|650)/, gradient: ['#1B5E20', '#2E7D32'], logo: 'verve' }, // Nigerian domestic network
  { brand: 'AMEX', pattern: /^3[47]/, gradient: ['#2E77BC', '#1A4F85'], logo: 'amex' },
  { brand: 'DISCOVER', pattern: /^6(?:011|5)/, gradient: ['#FF6000', '#D14900'], logo: 'discover' },
];

/**
 * @param {string} binOrPartialNumber - first 6-8 digits the user has typed so far (never store this)
 * @returns {{ brand: string, gradient: string[], logo: string } | null}
 */
function detectCardBrand(binOrPartialNumber) {
  const digits = (binOrPartialNumber || '').replace(/\D/g, '');
  if (digits.length < 1) return null;

  for (const entry of BRAND_PATTERNS) {
    if (entry.pattern.test(digits)) {
      return { brand: entry.brand, gradient: entry.gradient, logo: entry.logo };
    }
  }
  return { brand: 'UNKNOWN', gradient: ['#424242', '#212121'], logo: 'generic' };
}

module.exports = { detectCardBrand };
