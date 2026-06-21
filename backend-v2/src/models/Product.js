const mongoose = require('mongoose');

const productSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true, index: true },
    description: { type: String, default: '' },
    category: { type: String, required: true, index: true },
    price: { type: Number, required: true, min: 0 },
    discountPercent: { type: Number, default: 0, min: 0, max: 95 },
    images: { type: [String], default: [] },
    stockQuantity: { type: Number, default: 0, min: 0 },
    salesCount: { type: Number, default: 0 }, // incremented on each successful order line, drives ranking
    ratingAverage: { type: Number, default: 0 },
    ratingCount: { type: Number, default: 0 },

    // Admin can manually boost a product's carousel/algorithm visibility, time-bound
    isPromoted: { type: Boolean, default: false, index: true },
    promotionWeight: { type: Number, default: 0 }, // higher = ranked higher when isPromoted
    promotionEndsAt: { type: Date, default: null },

    isActive: { type: Boolean, default: true, index: true },
    createdByAdmin: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null },
  },
  { timestamps: true }
);

productSchema.index({ category: 1, isActive: 1 });

module.exports = mongoose.model('Product', productSchema);
