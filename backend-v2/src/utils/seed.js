/**
 * Run with: npm run seed
 * Creates the first superadmin account (from ADMIN_BOOTSTRAP_EMAIL/PASSWORD in .env) and a
 * handful of sample products so the storefront isn't empty on first run. Safe to re-run —
 * skips anything that already exists rather than duplicating.
 */
require('dotenv').config();
const { connectDatabase } = require('../config/database');
const User = require('../models/User');
const Wallet = require('../models/Wallet');
const Product = require('../models/Product');
const ThemeConfig = require('../models/ThemeConfig');

async function seed() {
  await connectDatabase();

  const adminEmail = process.env.ADMIN_BOOTSTRAP_EMAIL;
  const adminPassword = process.env.ADMIN_BOOTSTRAP_PASSWORD;

  if (!adminEmail || !adminPassword || adminPassword.length < 12) {
    console.error('[SEED] Set ADMIN_BOOTSTRAP_EMAIL and a 12+ char ADMIN_BOOTSTRAP_PASSWORD in .env first.');
    process.exit(1);
  }

  let admin = await User.findOne({ email: adminEmail.toLowerCase() });
  if (!admin) {
    admin = new User({
      email: adminEmail,
      name: 'Connexa Superadmin',
      role: 'superadmin',
      isEmailVerified: true,
      country: 'NG',
      preferredCurrency: 'NGN',
    });
    await admin.setPassword(adminPassword);
    await admin.save();
    await Wallet.create({ user: admin._id, balance: 0 });
    console.log(`[SEED] Created superadmin: ${adminEmail}`);
  } else {
    console.log(`[SEED] Superadmin already exists: ${adminEmail}`);
  }

  const existingTheme = await ThemeConfig.findOne({ singletonKey: 'GLOBAL_THEME' });
  if (!existingTheme) {
    await ThemeConfig.create({});
    console.log('[SEED] Created default theme config');
  }

  const sampleProducts = [
    { name: 'Wireless Earbuds Pro', category: 'Electronics', price: 15500, discountPercent: 20, stockQuantity: 120 },
    { name: 'Ankara Print Tote Bag', category: 'Fashion', price: 8500, discountPercent: 0, stockQuantity: 60 },
    { name: 'Smart Fitness Band', category: 'Electronics', price: 12000, discountPercent: 15, stockQuantity: 80 },
    { name: 'Non-Stick Cookware Set (5pc)', category: 'Home', price: 22000, discountPercent: 10, stockQuantity: 35 },
    { name: 'LED Ring Light with Tripod', category: 'Electronics', price: 9800, discountPercent: 0, stockQuantity: 50 },
  ];

  for (const p of sampleProducts) {
    const exists = await Product.findOne({ name: p.name });
    if (!exists) {
      await Product.create({ ...p, createdByAdmin: admin._id });
      console.log(`[SEED] Created product: ${p.name}`);
    }
  }

  console.log('[SEED] Done.');
  process.exit(0);
}

seed().catch((err) => {
  console.error('[SEED] Failed:', err);
  process.exit(1);
});
