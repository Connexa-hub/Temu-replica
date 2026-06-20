const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const DB_FILE = path.join(__dirname, 'db.json');

app.use(cors());
app.use(express.json());

// Main Dynamic State Holder (severs as local lightweight JSON database)
let db = {
  users: [
    { email: 'admin@temu.com', password: 'admin123', name: 'Temu Administrator', role: 'admin' },
    { email: 'user@temu.com', password: 'user123', name: 'Jack Buyer', role: 'user' }
  ],
  products: [],
  orders: [],
  orderItems: [],
  chatSessions: {} // Map of userId -> list of message items
};

// Seed products catalog list
const INITIAL_PRODUCTS = [
  {
    id: 1,
    name: "Clearance Premium Fleece Hoodie",
    description: "Super warm inner brushed winter heavy pullover. Oversized baggy fit suitable for casual outdoor activities.",
    category: "Fashion",
    price: 39.99,
    discountPercent: 75,
    imageUrl: "fashion_hoodie",
    stockQuantity: 45,
    salesCount: 12
  },
  {
    id: 2,
    name: "Ultra Bass wireless Noise-Canceling Buds",
    description: "Active high fidelity ambient cancelation with fast charge case. IPX7 waterproof rating for workout and heavy rain.",
    category: "Electronics",
    price: 129.99,
    discountPercent: 80,
    imageUrl: "electronics_buds",
    stockQuantity: 8,
    salesCount: 64
  },
  {
    id: 3,
    name: "Professional Dual Foil Electric Beard Shaver",
    description: "Skin friendly hypoallergenic foil blades. Includes intelligent clean station, dry/wet mode water washing support.",
    category: "Home & Living",
    price: 49.99,
    discountPercent: 40,
    imageUrl: "home_shaver",
    stockQuantity: 0,
    salesCount: 8
  },
  {
    id: 4,
    name: "Hydrating Hyaluronic Recovery Facial Cream",
    description: "Smooth moisturize repairs dry spots overnight. Organic lightweight formula suited for skin glow recovery therapy.",
    category: "Beauty & Health",
    price: 24.99,
    discountPercent: 15,
    imageUrl: "beauty_cream",
    stockQuantity: 120,
    salesCount: 30
  },
  {
    id: 5,
    name: "High Torque Retro RC Stunt Racing Car",
    description: "Interference free 2.4GHz remote control steering. Multi terrain shock absorbers for high speed backflips and spinning.",
    category: "Toys & Games",
    price: 89.99,
    discountPercent: 60,
    imageUrl: "toys_car",
    stockQuantity: 14,
    salesCount: 154
  }
];

// Load Database from File
function loadDatabase() {
  try {
    if (fs.existsSync(DB_FILE)) {
      const dataStr = fs.readFileSync(DB_FILE, 'utf8');
      const loaded = JSON.parse(dataStr);
      db = { ...db, ...loaded };
      console.log('Database file loaded successfully.');
    } else {
      db.products = [...INITIAL_PRODUCTS];
      saveDatabase();
      console.log('Database initialized with seed products.');
    }
  } catch (err) {
    console.error('Error loading database, falling back to default:', err);
    db.products = [...INITIAL_PRODUCTS];
  }
}

// Save Database to File
function saveDatabase() {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2), 'utf8');
  } catch (err) {
    console.error('Error saving database to file:', err);
  }
}

loadDatabase();

// ---------------------------------------------------------------------
// 1. AUTH API ENDPOINTS
// ---------------------------------------------------------------------
app.post('/api/auth/login', (req, res) => {
  const { email, password } = req.body;
  
  if (!email || !password) {
    return res.status(400).json({ error: 'Please enter registered credentials.' });
  }

  const user = db.users.find(u => u.email.toLowerCase() === email.toLowerCase() && u.password === password);
  
  if (!user) {
    return res.status(401).json({ error: 'Invalid profile email or security password.' });
  }

  res.json({
    email: user.email,
    name: user.name,
    role: user.role,
    token: `MOCK_JWT_STYLISH_KEY_FOR_${user.role.toUpperCase()}`
  });
});

app.post('/api/auth/register', (req, res) => {
  const { email, password, name, role } = req.body;

  if (!email || !password || !name) {
    return res.status(400).json({ error: 'Please enter all registration fields.' });
  }

  const exists = db.users.some(u => u.email.toLowerCase() === email.toLowerCase());
  if (exists) {
    return res.status(400).json({ error: 'Email account is already registered.' });
  }

  const newUser = {
    email,
    password,
    name,
    role: role || 'user'
  };

  db.users.push(newUser);
  saveDatabase();

  res.status(201).json({
    email: newUser.email,
    name: newUser.name,
    role: newUser.role,
    token: `MOCK_JWT_STYLISH_KEY_FOR_${newUser.role.toUpperCase()}`
  });
});

// ---------------------------------------------------------------------
// 2. PRODUCT MANAGEMENT ENDPOINTS
// ---------------------------------------------------------------------
app.get('/api/products', (req, res) => {
  res.json(db.products);
});

// Create product (Admin)
app.post('/api/products', (req, res) => {
  const { name, description, category, price, discountPercent, stockQuantity, imageUrl } = req.body;

  if (!name || !category || price === undefined || stockQuantity === undefined) {
    return res.status(400).json({ error: 'Required fields missing for product creation.' });
  }

  const nextId = db.products.reduce((max, p) => p.id > max ? p.id : max, 0) + 1;
  const newProduct = {
    id: nextId,
    name,
    description: description || '',
    category,
    price: Number(price),
    discountPercent: Number(discountPercent || 0),
    imageUrl: imageUrl || "fashion_hoodie",
    stockQuantity: Number(stockQuantity),
    salesCount: 0
  };

  db.products.push(newProduct);
  saveDatabase();
  res.status(201).json(newProduct);
});

// Update product (Admin)
app.put('/api/products/:id', (req, res) => {
  const pid = Number(req.params.id);
  const { name, description, category, price, discountPercent, stockQuantity } = req.body;

  const pIdx = db.products.findIndex(p => p.id === pid);
  if (pIdx === -1) {
    return res.status(404).json({ error: 'Product not found.' });
  }

  db.products[pIdx] = {
    ...db.products[pIdx],
    name: name !== undefined ? name : db.products[pIdx].name,
    description: description !== undefined ? description : db.products[pIdx].description,
    category: category !== undefined ? category : db.products[pIdx].category,
    price: price !== undefined ? Number(price) : db.products[pIdx].price,
    discountPercent: discountPercent !== undefined ? Number(discountPercent) : db.products[pIdx].discountPercent,
    stockQuantity: stockQuantity !== undefined ? Number(stockQuantity) : db.products[pIdx].stockQuantity
  };

  saveDatabase();
  res.json(db.products[pIdx]);
});

// Delete product (Admin)
app.delete('/api/products/:id', (req, res) => {
  const pid = Number(req.params.id);
  const exists = db.products.some(p => p.id === pid);
  if (!exists) {
    return res.status(404).json({ error: 'Product not found.' });
  }

  db.products = db.products.filter(p => p.id !== pid);
  saveDatabase();
  res.json({ success: true, message: 'Product removed.' });
});

// Reset product catalog (Admin)
app.post('/api/products/reseed', (req, res) => {
  db.products = [...INITIAL_PRODUCTS];
  saveDatabase();
  res.json({ success: true, products: db.products });
});

// ---------------------------------------------------------------------
// 3. ORDERS ENDPOINTS
// ---------------------------------------------------------------------
app.get('/api/orders', (req, res) => {
  res.json({
    orders: db.orders,
    orderItems: db.orderItems
  });
});

app.post('/api/orders', (req, res) => {
  const { totalAmount, items } = req.body; // items is array of { productId, quantity }

  if (!items || !Array.isArray(items) || items.length === 0) {
    return res.status(400).json({ error: 'Purchased cart items array is required.' });
  }

  // Pre-validate stock levels
  for (const item of items) {
    const prod = db.products.find(p => p.id === item.productId);
    if (!prod) {
      return res.status(400).json({ error: `Product ID key ${item.productId} does not exist.` });
    }
    if (prod.stockQuantity < item.quantity) {
      return res.status(400).json({ error: `Stock out: Only ${prod.stockQuantity} items remaining of ${prod.name}` });
    }
  }

  const nextOrderId = db.orders.reduce((max, o) => o.orderId > max ? o.orderId : max, 0) + 1;
  const orderTimestamp = Date.now();

  const savedOrderItems = [];
  
  // Deduct stock and compile checkout records
  for (const item of items) {
    const prod = db.products.find(p => p.id === item.productId);
    prod.stockQuantity -= item.quantity;
    prod.salesCount += item.quantity;

    const priceAtCheckout = prod.price * (1 - prod.discountPercent / 100.0);
    const itemRecord = {
      itemId: db.orderItems.length + savedOrderItems.length + 1,
      orderId: nextOrderId,
      productId: prod.id,
      productName: prod.name,
      category: prod.category,
      price: priceAtCheckout,
      quantity: item.quantity
    };
    savedOrderItems.push(itemRecord);
  }

  const calculatedTotal = savedOrderItems.sumOfAmount = savedOrderItems.reduce((acc, current) => acc + (current.price * current.quantity), 0);

  const newOrder = {
    orderId: nextOrderId,
    timestamp: orderTimestamp,
    totalAmount: totalAmount ? Number(totalAmount) : calculatedTotal
  };

  db.orders.push(newOrder);
  db.orderItems.push(...savedOrderItems);
  saveDatabase();

  res.status(201).json({
    success: true,
    order: newOrder,
    items: savedOrderItems
  });
});

// ---------------------------------------------------------------------
// 4. REAL-TIME CHAT SUPPORT HUB ENDPOINTS
// ---------------------------------------------------------------------

// Get all chat sessions (Admin overview)
app.get('/api/chat/sessions', (req, res) => {
  const sessions = Object.keys(db.chatSessions).map(userId => {
    const messages = db.chatSessions[userId];
    const lastMsg = messages[messages.length - 1];
    return {
      userId,
      messageCount: messages.length,
      lastMessage: lastMsg ? lastMsg.text : '',
      lastSender: lastMsg ? lastMsg.sender : '',
      timestamp: lastMsg ? lastMsg.timestamp : Date.now()
    };
  });
  res.json(sessions);
});

// Get chat history for specific user
app.get('/api/chat/:userId', (req, res) => {
  const userId = req.params.userId;
  const history = db.chatSessions[userId] || [];
  res.json(history);
});

// Post a chat message
app.post('/api/chat/:userId/message', (req, res) => {
  const userId = req.params.userId;
  const { text, sender, name } = req.body;

  if (!text || !sender) {
    return res.status(400).json({ error: 'Text content and sender fields are required.' });
  }

  if (!db.chatSessions[userId]) {
    db.chatSessions[userId] = [];
  }

  const newMsg = {
    msgId: db.chatSessions[userId].length + 1,
    sender, // 'user' or 'admin'
    name: name || sender,
    text,
    timestamp: Date.now()
  };

  db.chatSessions[userId].push(newMsg);
  
  // Simulated responses or triggers can happen here.
  // If user says hello and no admin is replying, we can append a smart bot co-pilot message after 1 second
  if (sender === 'user') {
    setTimeout(() => {
      // Auto-reply simulation helper if needed
      const botResponse = {
        msgId: db.chatSessions[userId].length + 1,
        sender: 'admin',
        name: 'Temu Bot Companion',
        text: `Hello ${name || 'Valued Customer'}! Our administrator support team has received your inquiry: "${text}". We will address your ticket shortly!`,
        timestamp: Date.now()
      };
      if (db.chatSessions[userId][db.chatSessions[userId].length - 1].sender === 'user') {
         db.chatSessions[userId].push(botResponse);
         saveDatabase();
      }
    }, 1000);
  }

  saveDatabase();
  res.status(201).json(newMsg);
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`================================================================`);
  console.log(`Temu Shop real-time e-commerce server started on port ${PORT}`);
  console.log(`API endpoints ready for client-admin synchronization.`);
  console.log(`Default administrator: admin@temu.com / admin123`);
  console.log(`Default buyer: user@temu.com / user123`);
  console.log(`================================================================`);
});
