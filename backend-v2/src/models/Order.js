const mongoose = require('mongoose');

const orderItemSchema = new mongoose.Schema(
  {
    product: { type: mongoose.Schema.Types.ObjectId, ref: 'Product', required: true },
    nameSnapshot: { type: String, required: true }, // preserve what the customer saw, even if product changes later
    priceSnapshot: { type: Number, required: true },
    quantity: { type: Number, required: true, min: 1 },
  },
  { _id: false }
);

const orderSchema = new mongoose.Schema(
  {
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    items: { type: [orderItemSchema], required: true, validate: (v) => v.length > 0 },
    totalAmount: { type: Number, required: true },
    currency: { type: String, default: 'NGN' },

    status: {
      type: String,
      enum: ['PENDING_PAYMENT', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'],
      default: 'PENDING_PAYMENT',
      index: true,
    },

    shippingAddress: {
      fullName: String,
      phone: String,
      addressLine1: String,
      addressLine2: String,
      city: String,
      state: String,
      country: String,
    },

    walletTransaction: { type: mongoose.Schema.Types.ObjectId, ref: 'WalletTransaction', default: null },

    trackingEvents: {
      type: [
        {
          status: String,
          note: String,
          at: { type: Date, default: Date.now },
        },
      ],
      default: [],
    },
  },
  { timestamps: true }
);

module.exports = mongoose.model('Order', orderSchema);
