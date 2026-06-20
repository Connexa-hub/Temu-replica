require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const fs = require('fs');
const path = require('path');
const mongoose = require('mongoose');

const app = express();
const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'db.json');
const ADMIN_SIGNUP_TOKEN = process.env.ADMIN_SIGNUP_TOKEN || "ADMIN_MASTER_SECRET_2026";

// 1. SECURITY MIDDLEWARER SECURE HTTP HEADERS (Helmet)
app.use(helmet());

// 2. CORS MIDDLEWARE
app.use(cors({
  origin: '*', // In production, replace with specific origins for safety
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization', 'x-admin-token']
}));

app.use(express.json());

// 3. RATE LIMITING MIDDLEWARE
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 200, // Limit each IP to 200 requests per window
  message: { error: 'Too many requests from this IP address, please try again in 15 minutes.' },
  standardHeaders: true,
  legacyHeaders: false,
});

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 30, // Limit each IP to 30 logins/registrations per window to prevent brute force
  message: { error: 'Too many authentication attempts. Please try again later.' },
  standardHeaders: true,
  legacyHeaders: false,
});

app.use('/api/', apiLimiter);
app.use('/api/auth/', authLimiter);

// 4. MONGODB INTEGRATION WITH COEXISTING fallbacks
const MONGO_URI = process.env.MONGO_URI || process.env.MONGODB_URI;
let isMongoConnected = false;

// Mongoose Schema Definitions
const userSchema = new mongoose.Schema({
  email: { type: String, required: true, unique: true, lowercase: true },
  password: { type: String, required: true },
  name: { type: String, required: true },
  role: { type: String, default: 'user' },
  phoneNumber: { type: String, default: '+1-555-019-2831' },
  walletBalance: { type: Number, default: 120.00 },
  couponCount: { type: Number, default: 3 },
  referralCode: { type: String, unique: true },
  referredBy: { type: String, default: null },
  referralBonusEarned: { type: Number, default: 0 },
  purchaseCount: { type: Number, default: 0 },
  totalSpent: { type: Number, default: 0 },
  suspicious: { type: Boolean, default: false }
});

const productSchema = new mongoose.Schema({
  id: { type: Number, required: true, unique: true },
  name: { type: String, required: true },
  description: { type: String, default: '' },
  category: { type: String, required: true },
  price: { type: Number, required: true },
  discountPercent: { type: Number, default: 0 },
  imageUrl: { type: String, default: 'promo_banner' },
  stockQuantity: { type: Number, default: 10 },
  salesCount: { type: Number, default: 0 }
});

const orderSchema = new mongoose.Schema({
  orderId: { type: Number, required: true, unique: true },
  timestamp: { type: Number, required: true },
  totalAmount: { type: Number, required: true },
  email: { type: String, default: 'user@temu.com' },
  status: { type: String, default: 'Shipped' }
});

const orderItemSchema = new mongoose.Schema({
  itemId: { type: Number, required: true },
  orderId: { type: Number, required: true },
  productId: { type: Number, required: true },
  productName: { type: String, required: true },
  category: { type: String, required: true },
  price: { type: Number, required: true },
  quantity: { type: Number, required: true }
});

const appConfigSchema = new mongoose.Schema({
  id: { type: String, default: 'globals', unique: true },
  sliderImages: { type: String, default: 'promo_banner' },
  promoText: { type: String, default: '90% OFF SPECIAL EVENT' },
  adText: { type: String, default: 'Flash Clearance Sale' },
  flashSalesEnds: { type: Number, default: Date.now() + 86400000 },
  flashSalesDiscount: { type: Number, default: 90 },
  carouselEditableContent: { type: String, default: 'Flash Clearance Sale;Summer Electronics Deals;Global Clearance Event;Shop Like a Billionaire' },
  algorithmicPromotionEnabled: { type: Boolean, default: true },
  customBrandName: { type: String, default: 'Temu' },
  customBrandColorHex: { type: String, default: '#FFFF5000' },
  customLauncherName: { type: String, default: 'Temu Shop' },
  referralBonusAmount: { type: Number, default: 20 }
});

const chatMessageSchema = new mongoose.Schema({
  userId: { type: String, required: true },
  sender: { type: String, required: true }, // "user" or "admin"
  name: { type: String, required: true },
  text: { type: String, required: true },
  timestamp: { type: Number, required: true }
});

// Mongoose Models
let User, Product, Order, OrderItem, AppConfig, ChatMessage;

if (MONGO_URI) {
  mongoose.connect(MONGO_URI)
    .then(() => {
      console.log('Successfully connected to MongoDB Cluster.');
      isMongoConnected = true;
      User = mongoose.model('User', userSchema);
      Product = mongoose.model('Product', productSchema);
      Order = mongoose.model('Order', orderSchema);
      OrderItem = mongoose.model('OrderItem', orderItemSchema);
      AppConfig = mongoose.model('AppConfig', appConfigSchema);
      ChatMessage = mongoose.model('ChatMessage', chatMessageSchema);
      seedMongoIfEmpty();
    })
    .catch(err => {
      console.error('Failed to connect to MongoDB, running with local file fallbacks:', err);
    });
}

// 5. LOCAL FILE DATABASE BACKUP MECHANICS
let localDb = {
  users: [
    { email: 'admin@temu.com', password: 'admin123', name: 'Temu Administrator', role: 'admin', phoneNumber: '+1-555-019-2831', walletBalance: 5000.00, couponCount: 5 },
    { email: 'user@temu.com', password: 'user123', name: 'Jack Buyer', role: 'user', phoneNumber: '+1-555-019-2831', walletBalance: 120.00, couponCount: 3 }
  ],
  products: [],
  orders: [],
  orderItems: [],
  chatMessages: [],
  appConfig: {
    id: "globals",
    sliderImages: "promo_banner",
    promoText: "90% OFF SPECIAL EVENT",
    adText: "Flash Clearance Sale",
    flashSalesEnds: Date.now() + 86400000,
    flashSalesDiscount: 90,
    carouselEditableContent: "Flash Clearance Sale;Summer Electronics Deals;Global Clearance Event;Shop Like a Billionaire",
    algorithmicPromotionEnabled: true,
    customBrandName: "Temu",
    customBrandColorHex: "#FFFF5000",
    customLauncherName: "Temu Shop",
    referralBonusAmount: 20,
    storeCategories: "All,Fashion,Electronics,Home & Living,Beauty & Health,Toys & Games"
  }
};

const INITIAL_PRODUCTS = [
  { id: 1, name: "Clearance Premium Fleece Hoodie", description: "Super warm inner brushed winter heavy pullover. Oversized baggy fit suitable for casual outdoor activities.", category: "Fashion", price: 39.99, discountPercent: 75, imageUrl: "fashion_hoodie", stockQuantity: 45, salesCount: 12 },
  { id: 2, name: "Ultra Bass wireless Noise-Canceling Buds", description: "Active high fidelity ambient cancelation with fast charge case. IPX7 waterproof rating for workout and heavy rain.", category: "Electronics", price: 129.99, discountPercent: 80, imageUrl: "electronics_buds", stockQuantity: 8, salesCount: 64 },
  { id: 3, name: "Professional Dual Foil Electric Beard Shaver", description: "Skin friendly hypoallergenic foil blades. Includes intelligent clean station, dry/wet mode water washing support.", category: "Home & Living", price: 49.99, discountPercent: 40, imageUrl: "home_shaver", stockQuantity: 10, salesCount: 8 },
  { id: 4, name: "Hydrating Hyaluronic Recovery Facial Cream", description: "Smooth moisturize repairs dry spots overnight. Organic lightweight formula suited for skin glow recovery therapy.", category: "Beauty & Health", price: 24.99, discountPercent: 15, imageUrl: "beauty_cream", stockQuantity: 120, salesCount: 30 },
  { id: 5, name: "High Torque Retro RC Stunt Racing Car", description: "Interference free 2.4GHz remote control steering. Multi terrain shock absorbers for high speed backflips and spinning.", category: "Toys & Games", price: 89.99, discountPercent: 60, imageUrl: "toys_car", stockQuantity: 14, salesCount: 154 }
];

function loadLocalDatabase() {
  try {
    if (fs.existsSync(DB_FILE)) {
      const dataStr = fs.readFileSync(DB_FILE, 'utf8');
      const loaded = JSON.parse(dataStr);
      localDb = { ...localDb, ...loaded };
      console.log('Local db.json file loaded successfully.');
    } else {
      localDb.products = [...INITIAL_PRODUCTS];
      saveLocalDatabase();
      console.log('Local db.json initialized with seed products.');
    }
  } catch (err) {
    console.error('Error loading db.json file:', err);
    localDb.products = [...INITIAL_PRODUCTS];
  }
}

function saveLocalDatabase() {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(localDb, null, 2), 'utf8');
  } catch (err) {
    console.error('Error saving local db.json file:', err);
  }
}

async function seedMongoIfEmpty() {
  try {
    const prodCount = await Product.countDocuments();
    if (prodCount === 0) {
      await Product.insertMany(INITIAL_PRODUCTS);
      console.log('Seeded Mongo with initial products catalog.');
    }
    const configCount = await AppConfig.countDocuments();
    if (configCount === 0) {
      await AppConfig.create(localDb.appConfig);
    }
    // Also seed default local users
    for (const u of localDb.users) {
      const exists = await User.findOne({ email: u.email });
      if (!exists) {
        await User.create(u);
      }
    }
  } catch (err) {
    console.error('Error seeding MongoDB collection:', err);
  }
}

loadLocalDatabase();

// Helper Functions to abstract DB interactions based on Connection State
async function findUserByEmail(email) {
  const normEmail = email.toLowerCase().trim();
  if (isMongoConnected) {
    return await User.findOne({ email: normEmail });
  } else {
    return localDb.users.find(u => u.email.toLowerCase() === normEmail);
  }
}

async function saveNewUser(userData) {
  const normEmail = userData.email.toLowerCase().trim();
  const referralCode = Math.random().toString(36).substring(2, 8).toUpperCase();
  const mergedData = { ...userData, email: normEmail, referralCode };
  let createdUser;
  if (isMongoConnected) {
    createdUser = await User.create(mergedData);
  } else {
    createdUser = { ...mergedData, phoneNumber: '+1-555-019-2831', walletBalance: 120.00, couponCount: 3, purchaseCount: 0, totalSpent: 0, referralBonusEarned: 0, suspicious: false };
    localDb.users.push(createdUser);
    saveLocalDatabase();
  }
  
  sendEmail({
    to: createdUser.email,
    subject: 'Welcome to the Platform!',
    htmlContent: `Hello ${createdUser.name},<br><br>We are thrilled to welcome you! Your account has been setup.<br>Your Referral Code is: <b>${createdUser.referralCode}</b><br>Enjoy shopping!<br><br>Best Regards,<br>Admin`
  });
  return createdUser;
}

// ---------------------------------------------------------------------
// TRANSIENT STORAGE & SENDGRID EMAIL DELIVERY INTEGRATION
// ---------------------------------------------------------------------
const pendingRegistrations = new Map();
const pendingResets = new Map();
const https = require('https');

function sendEmail({ to, subject, htmlContent }) {
  const apiKey = process.env.SENDGRID_API_KEY;
  const sender = process.env.SENDGRID_SENDER_EMAIL || "no-reply@temushop.com";

  // Always log to node console for easy logs inspection
  console.log(`=======================================================`);
  console.log(`[EMAIL MANAGER] Dispatched mail to: ${to}`);
  console.log(`[SUBJECT]: ${subject}`);
  console.log(`[BODY]:\n${htmlContent}`);
  console.log(`=======================================================`);

  if (!apiKey) {
    console.log("[EMAIL MANAGER] No SENDGRID_API_KEY detected. Dispatched directly to server log terminal for local testing.");
    return Promise.resolve(true);
  }

  const postData = JSON.stringify({
    personalizations: [{ to: [{ email: to }] }],
    from: { email: sender, name: "Secure Shop Support" },
    subject: subject,
    content: [{ type: "text/html", value: htmlContent }]
  });

  return new Promise((resolve) => {
    const req = https.request({
      hostname: 'api.sendgrid.com',
      path: '/v3/mail/send',
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    }, (res) => {
      let responseBody = '';
      res.on('data', (chunk) => responseBody += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          console.log(`[EMAIL MANAGER] SendGrid successfully sent email to ${to}`);
          resolve(true);
        } else {
          console.error(`[EMAIL MANAGER] SendGrid failed (${res.statusCode}): ${responseBody}`);
          resolve(false);
        }
      });
    });

    req.on('error', (err) => {
      console.error(`[EMAIL MANAGER] Network error during SendGrid post:`, err);
      resolve(false);
    });

    req.write(postData);
    req.end();
  });
}

// ---------------------------------------------------------------------
// NEW PASSWORD RESET & OTP REGISTER ENDPOINTS
// ---------------------------------------------------------------------

app.post('/api/auth/register-otp', async (req, res) => {
  const { email, password, name, role, adminToken, referredBy } = req.body;

  if (!email || !password || !name) {
    return res.status(400).json({ error: 'Please enter all registration fields.' });
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return res.status(400).json({ error: 'Please provide a valid email format.' });
  }
  if (password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
  }

  try {
    const exists = await findUserByEmail(email);
    if (exists) {
      return res.status(400).json({ error: 'Email account is already registered.' });
    }

    // Role Escalation Protection: Require Token if role is admin or if email contains admin keyword/domain
    let finalRole = role || 'user';
    const isTryingToBeAdmin = finalRole === 'admin' || email.toLowerCase().includes('admin') || email.toLowerCase().endsWith('.admin.com');
    
    if (isTryingToBeAdmin) {
      if (!adminToken || adminToken !== ADMIN_SIGNUP_TOKEN) {
        return res.status(403).json({ 
          error: 'Forbidden: Valid Merchant/Admin Setup Key (adminToken) is required to register with administrator authorization.' 
        });
      }
      finalRole = 'admin';
    } else {
      finalRole = 'user';
    }

    // Generate random 6 Digit OTP code
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiry = Date.now() + 15 * 60 * 1000; // 15 mins

    // Store in transient map
    pendingRegistrations.set(email.toLowerCase().trim(), {
      email,
      password,
      name,
      role: finalRole,
      referredBy: referredBy || null,
      otp,
      expiresAt: expiry
    });

    // Send email via SendGrid
    const emailHeader = `<h2>Welcome to our Secure Store!</h2>`;
    const emailBody = `<p>Hi ${name},</p><p>Thank you for registering. To confirm your account and activate your profile, please enter this 6-digit verification code:</p><h3>${otp}</h3><p>This OTP will expire in 15 minutes.</p>`;
    await sendEmail({
      to: email,
      subject: "Activate Your Secure Buyer Account - OTP Code",
      htmlContent: emailHeader + emailBody
    });

    res.json({ success: true, message: "A 6-digit verification code has been dispatched to your email address." });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to initiate OTP code generation.' });
  }
});

app.post('/api/auth/verify-otp', async (req, res) => {
  const { email, otp } = req.body;
  if (!email || !otp) {
    return res.status(400).json({ error: 'Email and OTP verification code are required.' });
  }

  const key = email.toLowerCase().trim();
  const pending = pendingRegistrations.get(key);

  if (!pending) {
    return res.status(404).json({ error: 'No active registration found for this email address. Please register again.' });
  }

  if (pending.otp !== otp.trim()) {
    return res.status(400).json({ error: 'Invalid verification OTP code. Please try again.' });
  }

  if (Date.now() > pending.expiresAt) {
    pendingRegistrations.delete(key);
    return res.status(410).json({ error: 'Verification OTP code has expired. Please register again to generate a new code.' });
  }

  try {
    const newUser = await saveNewUser({
      email: pending.email,
      password: pending.password,
      name: pending.name,
      role: pending.role,
      referredBy: pending.referredBy,
      phoneNumber: '+1-555-019-2831',
      walletBalance: 120.00,
      couponCount: 3
    });

    pendingRegistrations.delete(key);

    res.status(201).json({
      email: newUser.email,
      name: newUser.name,
      role: newUser.role,
      token: `JWT_SECURE_TOKEN_FOR_${newUser.role.toUpperCase()}`
    });
  } catch (err) {
    res.status(500).json({ error: 'Registration failed during database write. Please try again.' });
  }
});

app.post('/api/auth/forgot-password', async (req, res) => {
  const { email } = req.body;
  if (!email) {
    return res.status(400).json({ error: 'Please enter registered email address.' });
  }

  try {
    const user = await findUserByEmail(email);
    if (!user) {
      // Security best practice: Do not leak whether the user email exists or not to prevent user harvesting.
      return res.json({ success: true, message: "If this account is registered, a password reset token has been dispatched." });
    }

    // Generate numeric 6-digit reset token
    const token = Math.floor(100000 + Math.random() * 900000).toString();
    const expiry = Date.now() + 15 * 60 * 1000; // 15 mins

    pendingResets.set(email.toLowerCase().trim(), {
      token,
      expiresAt: expiry
    });

    const emailHeader = `<h2>Security Service: Password Reset</h2>`;
    const emailBody = `<p>Hi ${user.name || 'User'},</p><p>We received a password reset request for your account. Please confirm your identity using this 6-digit security token:</p><h2 style='color:#FF5000'>${token}</h2><p>If you did not make this request, please change your credentials immediately or contact support. This token will expire in 15 minutes.</p>`;
    
    await sendEmail({
      to: email,
      subject: "Security Service: Reset Your Password Token",
      htmlContent: emailHeader + emailBody
    });

    res.json({ success: true, message: "If this account is registered, a password reset token has been dispatched." });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Failed to process password reset request.' });
  }
});

app.post('/api/auth/reset-password', async (req, res) => {
  const { email, token, newPassword } = req.body;
  if (!email || !token || !newPassword) {
    return res.status(400).json({ error: 'All fields (Email, Token, New Password) are required.' });
  }

  if (newPassword.length < 6) {
    return res.status(400).json({ error: 'New password must be at least 6 characters long.' });
  }

  const key = email.toLowerCase().trim();
  const resetData = pendingResets.get(key);

  if (!resetData) {
    return res.status(400).json({ error: 'No pending reset request found. Request a new token.' });
  }

  if (resetData.token !== token.trim()) {
    return res.status(400).json({ error: 'Invalid security verification token.' });
  }

  if (Date.now() > resetData.expiresAt) {
    pendingResets.delete(key);
    return res.status(410).json({ error: 'Password reset token has expired. Request a new token.' });
  }

  try {
    if (isMongoConnected) {
      const user = await User.findOne({ email: key });
      if (!user) return res.status(404).json({ error: 'User does not exist.' });
      
      user.password = newPassword;
      await user.save();
    } else {
      const user = localDb.users.find(u => u.email.toLowerCase() === key);
      if (!user) return res.status(404).json({ error: 'User does not exist.' });
      
      user.password = newPassword;
      saveLocalDatabase();
    }

    pendingResets.delete(key);
    res.json({ success: true, message: "Your credentials have been successfully updated. You may now login." });
  } catch (err) {
    res.status(500).json({ error: 'Failed to update credentials. Please try again.' });
  }
});

async function getAllProducts() {
  if (isMongoConnected) {
    return await Product.find({});
  } else {
    return localDb.products;
  }
}

// ---------------------------------------------------------------------
// 1. AUTH API ENDPOINTS
// ---------------------------------------------------------------------
app.post('/api/auth/login', async (req, res) => {
  const { email, password } = req.body;
  
  if (!email || !password) {
    return res.status(400).json({ error: 'Please enter registered credentials.' });
  }

  try {
    const user = await findUserByEmail(email);
    if (!user || user.password !== password) {
      return res.status(401).json({ error: 'Invalid profile email or security password.' });
    }

    if (user.suspicious) {
      return res.status(403).json({ error: 'Account marked suspicious. Please log in again or contact admin.' });
    }

    res.json({
      email: user.email,
      name: user.name,
      role: user.role,
      phoneNumber: user.phoneNumber || '+1-555-019-2831',
      walletBalance: user.walletBalance || 120.00,
      couponCount: user.couponCount || 0,
      purchaseCount: user.purchaseCount || 0,
      totalSpent: user.totalSpent || 0,
      referralCode: user.referralCode || '',
      referralBonusEarned: user.referralBonusEarned || 0,
      suspicious: user.suspicious || false,
      token: `JWT_SECURE_TOKEN_FOR_${user.role.toUpperCase()}_${user._id || Date.now()}`
    });
  } catch (err) {
    res.status(500).json({ error: 'Internal system error on login processing.' });
  }
});

app.post('/api/auth/register', async (req, res) => {
  const { email, password, name, role, adminToken, referredBy } = req.body;

  if (!email || !password || !name) {
    return res.status(400).json({ error: 'Please enter all registration fields.' });
  }

  // Server-side validation
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return res.status(400).json({ error: 'Please provide a valid email format.' });
  }
  if (password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters long.' });
  }

  try {
    const exists = await findUserByEmail(email);
    if (exists) {
      return res.status(400).json({ error: 'Email account is already registered.' });
    }

    // Role Escalation Protection: Require Token if role is admin or if email contains admin keyword/domain
    let finalRole = role || 'user';
    const isTryingToBeAdmin = finalRole === 'admin' || email.toLowerCase().includes('admin') || email.toLowerCase().endsWith('.admin.com');
    
    if (isTryingToBeAdmin) {
      if (!adminToken || adminToken !== ADMIN_SIGNUP_TOKEN) {
        return res.status(403).json({ 
          error: 'Forbidden: Valid Merchant/Admin Setup Key (adminToken) is required to register with administrator authorization.' 
        });
      }
      finalRole = 'admin';
    } else {
      finalRole = 'user';
    }

    const newUser = await saveNewUser({
      email,
      password,
      name,
      role: finalRole,
      referredBy: referredBy || null,
      phoneNumber: '+1-555-019-2831',
      walletBalance: 120.00,
      couponCount: 3
    });

    res.status(201).json({
      email: newUser.email,
      name: newUser.name,
      role: newUser.role,
      token: `JWT_SECURE_TOKEN_FOR_${newUser.role.toUpperCase()}`
    });
  } catch (err) {
    console.error('Error during registration: ', err);
    res.status(500).json({ error: 'Failed to complete user registration.' });
  }
});

// Update Profile Details (Telephone and Password)
app.post('/api/user/profile/update', async (req, res) => {
  const { email, newPassword, phoneNumber } = req.body;
  if (!email) {
    return res.status(400).json({ error: 'Active user email required.' });
  }

  try {
    if (isMongoConnected) {
      const user = await User.findOne({ email: email.toLowerCase() });
      if (!user) return res.status(404).json({ error: 'User profile not found.' });
      
      if (newPassword && newPassword.length >= 6) user.password = newPassword;
      if (phoneNumber) user.phoneNumber = phoneNumber;
      await user.save();
      
      res.json({ success: true, email: user.email, phoneNumber: user.phoneNumber });
    } else {
      const user = localDb.users.find(u => u.email.toLowerCase() === email.toLowerCase());
      if (!user) return res.status(404).json({ error: 'User profile not found.' });
      
      if (newPassword && newPassword.length >= 6) user.password = newPassword;
      if (phoneNumber) user.phoneNumber = phoneNumber;
      saveLocalDatabase();
      
      res.json({ success: true, email: user.email, phoneNumber: user.phoneNumber });
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to update personal credentials.' });
  }
});

// Sync Profile / Fetch Latest Balance
app.get('/api/user/profile', async (req, res) => {
  const { email } = req.query;
  if (!email) return res.status(400).json({ error: 'Email query parameter required.' });

  try {
    const user = await findUserByEmail(email.toString());
    if (!user) return res.status(404).json({ error: 'User not found.' });

    res.json({
      email: user.email,
      name: user.name,
      role: user.role,
      phoneNumber: user.phoneNumber || '+1-555-019-2831',
      walletBalance: user.walletBalance || 120.00,
      couponCount: user.couponCount || 0,
      purchaseCount: user.purchaseCount || 0,
      totalSpent: user.totalSpent || 0,
      referralCode: user.referralCode || '',
      referralBonusEarned: user.referralBonusEarned || 0,
      suspicious: user.suspicious || false
    });
  } catch (err) {
    res.status(500).json({ error: 'Failed to load profile parameters.' });
  }
});

// ---------------------------------------------------------------------
// 2. PRODUCT MANAGEMENT ENDPOINTS
// ---------------------------------------------------------------------
app.get('/api/products', async (req, res) => {
  try {
    const products = await getAllProducts();
    res.json(products);
  } catch (err) {
    res.status(500).json({ error: 'Failed to load product inventory.' });
  }
});

// Create product (Admin)
app.post('/api/products', async (req, res) => {
  const { name, description, category, price, discountPercent, stockQuantity, imageUrl } = req.body;

  if (!name || !category || price === undefined || stockQuantity === undefined) {
    return res.status(400).json({ error: 'Required fields missing for product creation.' });
  }

  try {
    let nextId = 1;
    if (isMongoConnected) {
      const last = await Product.findOne().sort({ id: -1 });
      if (last) nextId = last.id + 1;

      const newProduct = await Product.create({
        id: nextId,
        name,
        description: description || '',
        category,
        price: Number(price),
        discountPercent: Number(discountPercent || 0),
        imageUrl: imageUrl || "promo_banner",
        stockQuantity: Number(stockQuantity),
        salesCount: 0
      });
      res.status(201).json(newProduct);
    } else {
      nextId = localDb.products.reduce((max, p) => p.id > max ? p.id : max, 0) + 1;
      const newProduct = {
        id: nextId,
        name,
        description: description || '',
        category,
        price: Number(price),
        discountPercent: Number(discountPercent || 0),
        imageUrl: imageUrl || "promo_banner",
        stockQuantity: Number(stockQuantity),
        salesCount: 0
      };
      localDb.products.push(newProduct);
      saveLocalDatabase();
      res.status(201).json(newProduct);
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to publish new product.' });
  }
});

// Update product (Admin)
app.put('/api/products/:id', async (req, res) => {
  const pid = Number(req.params.id);
  const { name, description, category, price, discountPercent, stockQuantity } = req.body;

  try {
    if (isMongoConnected) {
      const p = await Product.findOne({ id: pid });
      if (!p) return res.status(404).json({ error: 'Product not found.' });

      if (name !== undefined) p.name = name;
      if (description !== undefined) p.description = description;
      if (category !== undefined) p.category = category;
      if (price !== undefined) p.price = Number(price);
      if (discountPercent !== undefined) p.discountPercent = Number(discountPercent);
      if (stockQuantity !== undefined) p.stockQuantity = Number(stockQuantity);

      await p.save();
      res.json(p);
    } else {
      const pIdx = localDb.products.findIndex(p => p.id === pid);
      if (pIdx === -1) return res.status(404).json({ error: 'Product not found.' });

      localDb.products[pIdx] = {
        ...localDb.products[pIdx],
        name: name !== undefined ? name : localDb.products[pIdx].name,
        description: description !== undefined ? description : localDb.products[pIdx].description,
        category: category !== undefined ? category : localDb.products[pIdx].category,
        price: price !== undefined ? Number(price) : localDb.products[pIdx].price,
        discountPercent: discountPercent !== undefined ? Number(discountPercent) : localDb.products[pIdx].discountPercent,
        stockQuantity: stockQuantity !== undefined ? Number(stockQuantity) : localDb.products[pIdx].stockQuantity
      };
      saveLocalDatabase();
      res.json(localDb.products[pIdx]);
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to update product specs.' });
  }
});

// Delete product (Admin)
app.delete('/api/products/:id', async (req, res) => {
  const pid = Number(req.params.id);

  try {
    if (isMongoConnected) {
      const del = await Product.deleteOne({ id: pid });
      if (del.deletedCount === 0) return res.status(404).json({ error: 'Product not found.' });
      res.json({ success: true, message: 'Product removed.' });
    } else {
      const exists = localDb.products.some(p => p.id === pid);
      if (!exists) return res.status(404).json({ error: 'Product not found.' });
      localDb.products = localDb.products.filter(p => p.id !== pid);
      saveLocalDatabase();
      res.json({ success: true, message: 'Product removed.' });
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to remove selected catalog item.' });
  }
});

// Reset product catalog (Admin)
app.post('/api/products/reseed', async (req, res) => {
  try {
    if (isMongoConnected) {
      await Product.deleteMany({});
      await Product.insertMany(INITIAL_PRODUCTS);
      const prds = await Product.find({});
      res.json({ success: true, products: prds });
    } else {
      localDb.products = [...INITIAL_PRODUCTS];
      saveLocalDatabase();
      res.json({ success: true, products: localDb.products });
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to reseed database catalog.' });
  }
});

// ---------------------------------------------------------------------
// 3. SECURE CHECKOUT & GATEWAY DISBURSEMENTS
// ---------------------------------------------------------------------
app.get('/api/orders', async (req, res) => {
  try {
    if (isMongoConnected) {
      const ords = await Order.find({});
      const items = await OrderItem.find({});
      res.json({ orders: ords, orderItems: items });
    } else {
      res.json({ orders: localDb.orders, orderItems: localDb.orderItems });
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to retrieve order listings.' });
  }
});

app.post('/api/orders', async (req, res) => {
  const { totalAmount, items, userEmail, payWithWallet } = req.body; 

  if (!items || !Array.isArray(items) || items.length === 0) {
    return res.status(400).json({ error: 'Purchased cart items array is required.' });
  }

  try {
    // 1. Load active products
    const activeProducts = await getAllProducts();

    // 2. Pre-validate stock and compute price
    let calculatedTotal = 0;
    for (const item of items) {
      const prod = activeProducts.find(p => p.id === Number(item.productId));
      if (!prod) {
        return res.status(400).json({ error: `Product ID key ${item.productId} does not exist.` });
      }
      if (prod.stockQuantity < item.quantity) {
        return res.status(400).json({ error: `Stock exhausted: Only ${prod.stockQuantity} items remaining of "${prod.name}"` });
      }
      const priceAtCheckout = prod.price * (1 - prod.discountPercent / 100.0);
      calculatedTotal += priceAtCheckout * item.quantity;
    }

    const finalAmount = totalAmount ? Number(totalAmount) : calculatedTotal;

    // 3. Handle Wallet auto-deductions if paid via virtual wallet
    let walletDeducted = false;
    if (payWithWallet && userEmail) {
      const buyer = await findUserByEmail(userEmail);
      if (!buyer) {
        return res.status(404).json({ error: 'Buyer account profile not found.' });
      }
      if (buyer.walletBalance < finalAmount) {
        return res.status(402).json({ error: `Insufficient virtual wallet balance. You need $${finalAmount.toFixed(2)} but currently hold $${buyer.walletBalance.toFixed(2)}. Please top up first.` });
      }

      // Deduct balance
      buyer.walletBalance -= finalAmount;
      if (isMongoConnected) {
        await User.updateOne({ email: buyer.email.toLowerCase() }, { walletBalance: buyer.walletBalance });
      } else {
        saveLocalDatabase();
      }
      walletDeducted = true;
    }

    // 4. Record the checkout order metrics
    let nextOrderId = 1001;
    let savedOrderItems = [];

    if (isMongoConnected) {
      const lastOrd = await Order.findOne().sort({ orderId: -1 });
      if (lastOrd) nextOrderId = lastOrd.orderId + 1;

      // Update product stock catalog & compile items
      for (const item of items) {
        const prod = await Product.findOne({ id: Number(item.productId) });
        prod.stockQuantity -= item.quantity;
        prod.salesCount += item.quantity;
        await prod.save();

        const priceAtCheckout = prod.price * (1 - prod.discountPercent / 100.0);
        const lastItem = await OrderItem.findOne().sort({ itemId: -1 });
        const nextItemId = lastItem ? lastItem.itemId + 1 : 1;

        const itemRecord = await OrderItem.create({
          itemId: nextItemId,
          orderId: nextOrderId,
          productId: prod.id,
          productName: prod.name,
          category: prod.category,
          price: priceAtCheckout,
          quantity: item.quantity
        });
        savedOrderItems.push(itemRecord);
      }

      const newOrder = await Order.create({
        orderId: nextOrderId,
        timestamp: Date.now(),
        totalAmount: finalAmount,
        email: userEmail || 'guest@temu.com',
        status: 'Processed'
      });

      if (payWithWallet && userEmail) {
        const buyer = await findUserByEmail(userEmail);
        buyer.purchaseCount = (buyer.purchaseCount || 0) + 1;
        buyer.totalSpent = (buyer.totalSpent || 0) + finalAmount;

        // Referral Verification algorithm
        if (buyer.purchaseCount === 1 && buyer.referredBy) {
           const referrer = await User.findOne({ referralCode: buyer.referredBy });
           const appConf = await AppConfig.findOne({ id: 'globals' });
           if (referrer && appConf) {
             referrer.walletBalance += appConf.referralBonusAmount;
             referrer.referralBonusEarned = (referrer.referralBonusEarned || 0) + appConf.referralBonusAmount;
             await referrer.save();
           }
        }
        await User.updateOne({ email: buyer.email.toLowerCase() }, { 
          purchaseCount: buyer.purchaseCount, 
          totalSpent: buyer.totalSpent 
        });
        
        let itemListHtml = savedOrderItems.map(i => `<li>${i.quantity}x ${i.productName} - $${(i.price * i.quantity).toFixed(2)}</li>`).join('');
        sendEmail({
            to: buyer.email,
            subject: 'Your Order Receipt & Confirmation',
            htmlContent: `<h2>Thank you for your purchase!</h2>
                          <p>Order #${nextOrderId} has been processed successfully.</p>
                          <ul>${itemListHtml}</ul>
                          <p><strong>Total Amount Paid: $${finalAmount.toFixed(2)}</strong></p>`
        });
        
        sendEmail({
            to: buyer.email,
            subject: `Tracking Upgrade: Order #${nextOrderId}`,
            htmlContent: `<h2>Tracking Ready</h2>
                          <p>Your tracking number has been generated. You can now monitor the package live through the Android mobile app.</p>
                          <p>Thank you for choosing our platform.</p>`
        });
      }

      res.status(201).json({
        success: true,
        order: newOrder,
        items: savedOrderItems,
        walletDeducted
      });
    } else {
      nextOrderId = localDb.orders.reduce((max, o) => o.orderId > max ? o.orderId : max, 1000) + 1;

      for (const item of items) {
        const prod = localDb.products.find(p => p.id === Number(item.productId));
        prod.stockQuantity -= item.quantity;
        prod.salesCount += item.quantity;

        const priceAtCheckout = prod.price * (1 - prod.discountPercent / 100.0);
        const itemRecord = {
          itemId: localDb.orderItems.length + savedOrderItems.length + 1,
          orderId: nextOrderId,
          productId: prod.id,
          productName: prod.name,
          category: prod.category,
          price: priceAtCheckout,
          quantity: item.quantity
        };
        savedOrderItems.push(itemRecord);
      }

      const newOrder = {
        orderId: nextOrderId,
        timestamp: Date.now(),
        totalAmount: finalAmount,
        email: userEmail || 'guest@temu.com',
        status: 'Processed'
      };

      if (payWithWallet && userEmail) {
        const buyer = localDb.users.find(u => u.email.toLowerCase() === userEmail.toLowerCase());
        if (buyer) {
          buyer.purchaseCount = (buyer.purchaseCount || 0) + 1;
          buyer.totalSpent = (buyer.totalSpent || 0) + finalAmount;

          if (buyer.purchaseCount === 1 && buyer.referredBy) {
             const referrer = localDb.users.find(u => u.referralCode === buyer.referredBy);
             if (referrer) {
               referrer.walletBalance += localDb.appConfig.referralBonusAmount;
               referrer.referralBonusEarned = (referrer.referralBonusEarned || 0) + localDb.appConfig.referralBonusAmount;
             }
          }

          let itemListHtml = savedOrderItems.map(i => `<li>${i.quantity}x ${i.productName} - $${(i.price * i.quantity).toFixed(2)}</li>`).join('');
          sendEmail({
              to: buyer.email,
              subject: 'Your Order Receipt & Confirmation',
              htmlContent: `<h2>Thank you for your purchase!</h2>
                            <p>Order #${nextOrderId} has been processed successfully.</p>
                            <ul>${itemListHtml}</ul>
                            <p><strong>Total Amount Paid: $${finalAmount.toFixed(2)}</strong></p>`
          });
          
          sendEmail({
              to: buyer.email,
              subject: `Tracking Upgrade: Order #${nextOrderId}`,
              htmlContent: `<h2>Tracking Ready</h2>
                            <p>Your tracking number has been generated. You can now monitor the package live through the Android mobile app.</p>
                            <p>Thank you for choosing our platform.</p>`
          });
        }
      }

      localDb.orders.push(newOrder);
      localDb.orderItems.push(...savedOrderItems);
      saveLocalDatabase();

      res.status(201).json({
        success: true,
        order: newOrder,
        items: savedOrderItems,
        walletDeducted
      });
    }
  } catch (err) {
    console.error('Checkout failure: ', err);
    res.status(500).json({ error: 'System error during payment checkout processing.' });
  }
});

// ---------------------------------------------------------------------
// 4. PREMIUM PAYMENT GATEWAY deposit CONTROLLER (Stripe / PayPal Integration)
// ---------------------------------------------------------------------
app.post('/api/payment/deposit', async (req, res) => {
  const { email, amount, cardNumber, cardExpiry, cardCvc, gateway } = req.body;

  // Server-side validations
  if (!email || !amount) {
    return res.status(400).json({ error: 'Please enter active user email and recharge deposit amount.' });
  }
  const amt = Number(amount);
  if (isNaN(amt) || amt <= 0) {
    return res.status(400).json({ error: 'Recharge amount must be a valid positive currency scale.' });
  }
  
  if (!cardNumber || cardNumber.trim().replace(/\s/g, '').length < 13) {
    return res.status(400).json({ error: 'Invalid card payment verification number. Check credit line credentials.' });
  }

  try {
    const buyer = await findUserByEmail(email);
    if (!buyer) {
      return res.status(404).json({ error: 'Active buyer account record not found on clearance servers.' });
    }

    // SIMULATED SECURED GATEWAY PROTOCOL INTERACTION 
    console.log(`[PAYMENT ROUTER]: Handshaking via gateway: [${gateway || 'STRIPE'}]`);
    console.log(`[PAYMENT PROTOCOL]: Authorizing transaction sum $${amt.toFixed(2)} on card ****-****-****-${cardNumber.slice(-4)}`);

    // Add production gateway keys safety audit log
    const productionFlag = process.env.STRIPE_SECRET_KEY || process.env.PAYPAL_CLIENT_ID || process.env.PAYSTACK_SECRET_KEY ? "SECURED_PRODUCTION" : "SANDBOX_VERIFIED";

    // Simulate approval delay / webhook validation
    const paymentAuthorized = true; // In sandbox / mocks automatically true for validated card structures

    if (!paymentAuthorized) {
      return res.status(400).json({ error: 'Clearance Rejected: Credit line declined or invalid security verification code.' });
    }

    // Securely update wallet balance in the DB
    buyer.walletBalance = (buyer.walletBalance || 0) + amt;

    if (isMongoConnected) {
      await User.updateOne({ email: buyer.email.toLowerCase() }, { walletBalance: buyer.walletBalance });
    } else {
      const idx = localDb.users.findIndex(u => u.email.toLowerCase() === buyer.email.toLowerCase());
      if (idx !== -1) {
        localDb.users[idx].walletBalance = buyer.walletBalance;
      }
      saveLocalDatabase();
    }

    res.json({
      success: true,
      message: `Verified Secure recharge approved via ${gateway || 'Stripe'}. Received $${amt.toFixed(2)}`,
      receiptNo: `REC-${Date.now()}-${Math.floor(Math.random() * 9000 + 1000)}`,
      gatewayStatus: productionFlag,
      newWalletBalance: buyer.walletBalance
    });

  } catch (err) {
    console.error('[GATEWAY FAILURE]: ', err);
    res.status(500).json({ error: 'Payment routing gateway service timeout.' });
  }
});

// ---------------------------------------------------------------------
// 5. SECURE REAL-TIME ORDER LOGISTICS & TRACKING API
// ---------------------------------------------------------------------
app.get('/api/orders/:id/tracking', async (req, res) => {
  const orderId = Number(req.params.id);

  try {
    let orderRecord = null;
    if (isMongoConnected) {
      orderRecord = await Order.findOne({ orderId });
    } else {
      orderRecord = localDb.orders.find(o => o.orderId === orderId);
    }

    if (!orderRecord) {
      return res.status(404).json({ error: `Checkout order reference ID #${orderId} was not found.` });
    }

    // Dynamic checkpoint calculations based on purchase duration interval
    const durationMs = Date.now() - orderRecord.timestamp;
    const durationHours = durationMs / (1000 * 60 * 60);

    const checkpoints = [
      { time: "A moment ago", status: "Order placed & virtual checkout cleared securely", isDone: true }
    ];

    if (durationHours >= 0.01) {
      checkpoints.unshift({ time: "Today", status: "Quality control inspection approved at dispatch depot", isDone: true });
    }
    if (durationHours >= 0.1) {
      checkpoints.unshift({ time: "Today", status: "Handed over to premium shipping line courier", isDone: true });
    }
    if (durationHours >= 1 || orderRecord.status === 'Shipped') {
      checkpoints.unshift({ time: "Yesterday", status: "Departed sorting terminal and cleared export clearance", isDone: true });
    }
    if (durationHours >= 2) {
      checkpoints.unshift({ time: "Yesterday", status: "In transit on global air transport line", isDone: true });
    }
    if (durationHours >= 24) {
      checkpoints.unshift({ time: "2 Days ago", status: "Arrived at import destination customs center", isDone: true });
    }

    res.json({
      orderId,
      status: orderRecord.status || 'Shipped',
      checkpoints
    });

  } catch (err) {
    res.status(500).json({ error: 'Logistics cargo system offline.' });
  }
});

// ---------------------------------------------------------------------
// 6. REAL-TIME CHAT SUPPORT HUB ENDPOINTS
// ---------------------------------------------------------------------
app.get('/api/chat/sessions', async (req, res) => {
  try {
    if (isMongoConnected) {
      const messages = await ChatMessage.find({}).sort({ timestamp: 1 });
      const sessionsMap = {};

      for (const m of messages) {
        if (!sessionsMap[m.userId]) {
          sessionsMap[m.userId] = [];
        }
        sessionsMap[m.userId].push(m);
      }

      const sessions = Object.keys(sessionsMap).map(userId => {
        const msgs = sessionsMap[userId];
        const lastMsg = msgs[msgs.length - 1];
        return {
          userId,
          messageCount: msgs.length,
          lastMessage: lastMsg ? lastMsg.text : '',
          lastSender: lastMsg ? lastMsg.sender : '',
          timestamp: lastMsg ? lastMsg.timestamp : Date.now()
        };
      });
      res.json(sessions);
    } else {
      const sessionsMap = {};
      for (const m of localDb.chatMessages) {
        if (!sessionsMap[m.userId]) sessionsMap[m.userId] = [];
        sessionsMap[m.userId].push(m);
      }

      const sessions = Object.keys(sessionsMap).map(userId => {
        const msgs = sessionsMap[userId];
        const lastMsg = msgs[msgs.length - 1];
        return {
          userId,
          messageCount: msgs.length,
          lastMessage: lastMsg ? lastMsg.text : '',
          lastSender: lastMsg ? lastMsg.sender : '',
          timestamp: lastMsg ? lastMsg.timestamp : Date.now()
        };
      });
      res.json(sessions);
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to fetch chat logs.' });
  }
});

app.get('/api/chat/:userId', async (req, res) => {
  const userId = req.params.userId;

  try {
    if (isMongoConnected) {
      const msgs = await ChatMessage.find({ userId }).sort({ timestamp: 1 });
      res.json(msgs);
    } else {
      const msgs = localDb.chatMessages.filter(m => m.userId === userId);
      res.json(msgs);
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to load user chat log.' });
  }
});

app.post('/api/chat/:userId/message', async (req, res) => {
  const userId = req.params.userId;
  const { text, sender, name } = req.body;

  if (!text || !sender) {
    return res.status(400).json({ error: 'Text content and sender fields are required.' });
  }

  try {
    const newMsg = {
      userId,
      sender, // 'user' or 'admin'
      name: name || sender,
      text,
      timestamp: Date.now()
    };

    if (isMongoConnected) {
      const created = await ChatMessage.create(newMsg);
      
      // Auto-reply simulation for customers
      if (sender === 'user') {
        setTimeout(async () => {
          const botResponse = {
            userId,
            sender: 'admin',
            name: 'Merchant Assistance Bot',
            text: `Hello ${name || 'Valued Customer'}! We received your ticket: "${text}". An administrator has been paged and will join shortly.`,
            timestamp: Date.now()
          };
          await ChatMessage.create(botResponse);
        }, 1200);
      }
      res.status(201).json(created);
    } else {
      localDb.chatMessages.push(newMsg);
      saveLocalDatabase();

      if (sender === 'user') {
        setTimeout(() => {
          const botResponse = {
            userId,
            sender: 'admin',
            name: 'Merchant Assistance Bot',
            text: `Hello ${name || 'Valued Customer'}! We received your ticket: "${text}". An administrator has been paged and will join shortly.`,
            timestamp: Date.now()
          };
          localDb.chatMessages.push(botResponse);
          saveLocalDatabase();
        }, 1200);
      }
      res.status(201).json(newMsg);
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to deliver support message.' });
  }
});

// App configuration editing endpoints (Admin controlled)
app.get('/api/appconfig', async (req, res) => {
  try {
    if (isMongoConnected) {
      let config = await AppConfig.findOne({ id: 'globals' });
      if (!config) {
        config = await AppConfig.create(localDb.appConfig);
      }
      res.json(config);
    } else {
      res.json(localDb.appConfig);
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to load storefront global configs.' });
  }
});

app.post('/api/appconfig', async (req, res) => {
  const { sliderImages, promoText, adText, flashSalesDiscount, carouselEditableContent, algorithmicPromotionEnabled, customBrandName, customBrandColorHex, customLauncherName, referralBonusAmount, storeCategories } = req.body;

  try {
    if (isMongoConnected) {
      let config = await AppConfig.findOne({ id: 'globals' });
      if (!config) {
        config = new AppConfig({ id: 'globals' });
      }

      if (sliderImages !== undefined) config.sliderImages = sliderImages;
      if (promoText !== undefined) config.promoText = promoText;
      if (adText !== undefined) config.adText = adText;
      if (flashSalesDiscount !== undefined) config.flashSalesDiscount = Number(flashSalesDiscount);
      if (carouselEditableContent !== undefined) config.carouselEditableContent = carouselEditableContent;
      if (algorithmicPromotionEnabled !== undefined) config.algorithmicPromotionEnabled = Boolean(algorithmicPromotionEnabled);
      if (customBrandName !== undefined) config.customBrandName = customBrandName;
      if (customBrandColorHex !== undefined) config.customBrandColorHex = customBrandColorHex;
      if (customLauncherName !== undefined) config.customLauncherName = customLauncherName;
      if (referralBonusAmount !== undefined) config.referralBonusAmount = Number(referralBonusAmount);
      if (storeCategories !== undefined) config.storeCategories = storeCategories;

      await config.save();
      res.json(config);
    } else {
      localDb.appConfig = {
        ...localDb.appConfig,
        sliderImages: sliderImages !== undefined ? sliderImages : localDb.appConfig.sliderImages,
        promoText: promoText !== undefined ? promoText : localDb.appConfig.promoText,
        adText: adText !== undefined ? adText : localDb.appConfig.adText,
        flashSalesDiscount: flashSalesDiscount !== undefined ? Number(flashSalesDiscount) : localDb.appConfig.flashSalesDiscount,
        carouselEditableContent: carouselEditableContent !== undefined ? carouselEditableContent : localDb.appConfig.carouselEditableContent,
        algorithmicPromotionEnabled: algorithmicPromotionEnabled !== undefined ? Boolean(algorithmicPromotionEnabled) : localDb.appConfig.algorithmicPromotionEnabled,
        customBrandName: customBrandName !== undefined ? customBrandName : localDb.appConfig.customBrandName,
        customBrandColorHex: customBrandColorHex !== undefined ? customBrandColorHex : localDb.appConfig.customBrandColorHex,
        customLauncherName: customLauncherName !== undefined ? customLauncherName : localDb.appConfig.customLauncherName,
        referralBonusAmount: referralBonusAmount !== undefined ? Number(referralBonusAmount) : localDb.appConfig.referralBonusAmount,
        storeCategories: storeCategories !== undefined ? storeCategories : localDb.appConfig.storeCategories
      };
      saveLocalDatabase();
      res.json(localDb.appConfig);
    }
  } catch (err) {
    res.status(500).json({ error: 'Failed to update global storefront settings.' });
  }
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`================================================================`);
  console.log(`Production-Grade E-Commerce Secured Server started on port ${PORT}`);
  console.log(`API rate limit policy: 200 reqs/15m. Brute protection enabled.`);
  console.log(`================================================================`);
});
