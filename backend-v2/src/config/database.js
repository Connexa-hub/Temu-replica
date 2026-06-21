const mongoose = require('mongoose');

let isConnected = false;

async function connectDatabase() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error('[DB] MONGODB_URI is not set. The app cannot start without a real database.');
    process.exit(1);
  }

  mongoose.set('strictQuery', true);

  try {
    await mongoose.connect(uri, {
      serverSelectionTimeoutMS: 8000,
    });
    isConnected = true;
    console.log('[DB] Connected to MongoDB');
  } catch (err) {
    console.error('[DB] Connection failed:', err.message);
    process.exit(1);
  }

  mongoose.connection.on('disconnected', () => {
    isConnected = false;
    console.warn('[DB] Disconnected from MongoDB');
  });

  mongoose.connection.on('reconnected', () => {
    isConnected = true;
    console.log('[DB] Reconnected to MongoDB');
  });
}

function getDbStatus() {
  return isConnected;
}

module.exports = { connectDatabase, getDbStatus };
