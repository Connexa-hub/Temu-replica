const mongoose = require('mongoose');

const bannerSchema = new mongoose.Schema(
  {
    title: { type: String, required: true, trim: true },
    subtitle: { type: String, default: '', trim: true },
    imageUrl: { type: String, required: true },
    imageUrlDark: { type: String, default: null }, // optional dark-mode variant
    linkType: { type: String, enum: ['PRODUCT', 'CATEGORY', 'EXTERNAL_URL', 'NONE'], default: 'NONE' },
    linkValue: { type: String, default: null }, // productId, categorySlug, or full URL depending on linkType

    placement: {
      type: String,
      enum: ['HOME_CAROUSEL', 'HOME_PROMO_STRIP', 'CATEGORY_TOP', 'FLASH_SALE'],
      default: 'HOME_CAROUSEL',
      index: true,
    },

    sortOrder: { type: Number, default: 0, index: true }, // admin drag-to-reorder
    isActive: { type: Boolean, default: true, index: true },

    // Scheduling — lets admin queue up promos in advance, algorithm picks active ones
    startsAt: { type: Date, default: null },
    endsAt: { type: Date, default: null },

    // Optional geo/country targeting
    targetCountries: { type: [String], default: [] }, // empty = all countries

    createdByAdmin: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  },
  { timestamps: true }
);

bannerSchema.index({ placement: 1, isActive: 1, sortOrder: 1 });

module.exports = mongoose.model('Banner', bannerSchema);
