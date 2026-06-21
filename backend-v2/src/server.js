require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const path = require('path');

const { connectDatabase, getDbStatus } = require('./config/database');

const app = express();
const PORT = process.env.PORT || 3000;

app.set('trust proxy', 1); // accurate client IP behind Render/other reverse proxies
app.use(helmet());
app.use(
  cors({
    origin: process.env.NODE_ENV === 'production' ? (process.env.ALLOWED_ORIGINS || '').split(',') : '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  })
);

// ---------------------------------------------------------------------
// WEBHOOK ROUTES MUST BE MOUNTED *BEFORE* express.json() WITH RAW BODY,
// because Monnify/Paystack signature verification needs the exact raw bytes
// that were sent — re-serializing a parsed JSON object would change the
// signature and make every legitimate webhook look forged.
// ---------------------------------------------------------------------
const paymentRoutes = require('./routes/paymentRoutes');

app.use(
  '/api/payments/webhooks',
  express.raw({ type: 'application/json' }),
  (req, res, next) => {
    req.rawBody = req.body; // Buffer, used by gateway.verifyWebhookSignature()
    next();
  }
);

// Standard JSON body parsing for everything else
app.use(express.json({ limit: '2mb' }));

// Serve uploaded banner/brand images
app.use('/uploads', express.static(path.join(__dirname, '..', 'uploads')));

// ---------------------------------------------------------------------
// RATE LIMITING
// ---------------------------------------------------------------------
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: Number(process.env.API_RATE_LIMIT_PER_15MIN) || 200,
  message: { error: 'Too many requests. Please try again in 15 minutes.' },
  standardHeaders: true,
  legacyHeaders: false,
});
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: Number(process.env.AUTH_RATE_LIMIT_PER_15MIN) || 30,
  message: { error: 'Too many authentication attempts. Please try again later.' },
  standardHeaders: true,
  legacyHeaders: false,
});

app.use('/api/', apiLimiter);
app.use('/api/auth/', authLimiter);

// ---------------------------------------------------------------------
// HEALTH CHECK
// ---------------------------------------------------------------------
app.get('/', (req, res) => {
  res.json({
    status: 'Connexa Shop API',
    version: '2.0.0',
    dbConnected: getDbStatus(),
  });
});
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', dbConnected: getDbStatus(), timestamp: new Date().toISOString() });
});

// ---------------------------------------------------------------------
// ROUTES
// ---------------------------------------------------------------------
app.use('/api/auth', require('./routes/authRoutes'));
app.use('/api/products', require('./routes/productRoutes'));
app.use('/api/orders', require('./routes/orderRoutes'));
app.use('/api/payments', paymentRoutes);
app.use('/api/utility', require('./routes/utilityRoutes'));
app.use('/api/admin', require('./routes/adminRoutes'));

// ---------------------------------------------------------------------
// 404 + ERROR HANDLERS
// ---------------------------------------------------------------------
app.use((req, res) => {
  res.status(404).json({ error: `No route for ${req.method} ${req.originalUrl}` });
});

// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error('[UNHANDLED ERROR]', err);
  if (err.message?.includes('Only JPEG, PNG, or WEBP')) {
    return res.status(400).json({ error: err.message });
  }
  if (err.name === 'ValidationError') {
    return res.status(400).json({ error: err.message });
  }
  res.status(500).json({ error: 'Internal server error' });
});

// ---------------------------------------------------------------------
// STARTUP
// ---------------------------------------------------------------------
async function start() {
  // Fail fast on missing critical secrets rather than booting into a broken state
  const required = ['MONGODB_URI', 'JWT_ACCESS_SECRET', 'JWT_REFRESH_SECRET'];
  const missing = required.filter((key) => !process.env[key]);
  if (missing.length > 0) {
    console.error(`[STARTUP] Missing required environment variables: ${missing.join(', ')}`);
    console.error('[STARTUP] Copy .env.example to .env and fill in real values before starting.');
    process.exit(1);
  }

  await connectDatabase();

  app.listen(PORT, '0.0.0.0', () => {
    console.log('================================================================');
    console.log(`Connexa Shop API listening on port ${PORT}`);
    console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
    console.log('================================================================');
  });
}

start();

module.exports = app;
