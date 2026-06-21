const Mailjet = require('node-mailjet');

let mailjet = null;
function getClient() {
  if (!process.env.MAILJET_API_KEY || !process.env.MAILJET_SECRET_KEY) return null;
  if (!mailjet) mailjet = Mailjet.apiConnect(process.env.MAILJET_API_KEY, process.env.MAILJET_SECRET_KEY);
  return mailjet;
}

async function sendOtpEmail(toEmail, code, purpose) {
  const client = getClient();
  const subject = purpose === 'REGISTER' ? 'Verify your Connexa account' : 'Reset your Connexa password';
  const text = `Your Connexa verification code is ${code}. It expires in 10 minutes. If you did not request this, ignore this email.`;

  if (!client) {
    // No mail credentials configured — fail loudly in logs rather than silently pretending to send.
    console.warn(`[MAIL] Mailjet not configured. Would have sent to ${toEmail}: "${text}"`);
    return { delivered: false, reason: 'MAIL_NOT_CONFIGURED' };
  }

  try {
    await client.post('send', { version: 'v3.1' }).request({
      Messages: [
        {
          From: { Email: process.env.MAIL_FROM_ADDRESS, Name: process.env.MAIL_FROM_NAME || 'Connexa' },
          To: [{ Email: toEmail }],
          Subject: subject,
          TextPart: text,
        },
      ],
    });
    return { delivered: true };
  } catch (err) {
    console.error('[MAIL] Mailjet send failed:', err.message);
    return { delivered: false, reason: 'SEND_FAILED' };
  }
}

module.exports = { sendOtpEmail };
