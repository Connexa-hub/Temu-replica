const express = require('express');
const router = express.Router();
const Joi = require('joi');

const { requireAuth, requireRole, optionalAuth } = require('../middleware/auth');
const { validateBody } = require('../middleware/validate');
const Product = require('../models/Product');
const Review = require('../models/Review');
const { scoreProducts } = require('../services/rankingAlgorithm');

// ---------------------------------------------------------------------
// GET /api/products  — public listing, filterable
// ---------------------------------------------------------------------
router.get('/', async (req, res, next) => {
  try {
    const { category, search, page = 1, limit = 24 } = req.query;
    const filter = { isActive: true };
    if (category) filter.category = category;
    if (search) filter.name = new RegExp(search, 'i');

    const [products, total] = await Promise.all([
      Product.find(filter)
        .sort({ createdAt: -1 })
        .skip((page - 1) * limit)
        .limit(Number(limit)),
      Product.countDocuments(filter),
    ]);

    res.json({ products, total, page: Number(page), limit: Number(limit) });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// GET /api/products/carousel — ranked by the scoring algorithm, used for homepage rails
// ---------------------------------------------------------------------
router.get('/carousel', async (req, res, next) => {
  try {
    const { category, limit = 12 } = req.query;
    const filter = { isActive: true };
    if (category) filter.category = category;

    const candidates = await Product.find(filter).limit(200); // bounded candidate pool for performance
    const ranked = scoreProducts(candidates);

    res.json(ranked.slice(0, Number(limit)).map((r) => ({ ...r.product.toObject(), _rankScore: r.score })));
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// GET /api/products/:id
// ---------------------------------------------------------------------
router.get('/:id', async (req, res, next) => {
  try {
    const product = await Product.findById(req.params.id);
    if (!product || !product.isActive) return res.status(404).json({ error: 'Product not found' });
    res.json(product);
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// GET /api/products/:id/reviews
// ---------------------------------------------------------------------
router.get('/:id/reviews', async (req, res, next) => {
  try {
    const reviews = await Review.find({ product: req.params.id }).populate('user', 'name').sort({ createdAt: -1 });
    res.json(reviews);
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// POST /api/products/:id/reviews — auth required, one per user per product
// ---------------------------------------------------------------------
router.post(
  '/:id/reviews',
  requireAuth,
  validateBody(Joi.object({ rating: Joi.number().min(1).max(5).required(), comment: Joi.string().max(1000).allow('') })),
  async (req, res, next) => {
    try {
      const product = await Product.findById(req.params.id);
      if (!product) return res.status(404).json({ error: 'Product not found' });

      const review = await Review.findOneAndUpdate(
        { product: product._id, user: req.user._id },
        { rating: req.body.rating, comment: req.body.comment || '' },
        { upsert: true, new: true, setDefaultsOnInsert: true }
      );

      const stats = await Review.aggregate([
        { $match: { product: product._id } },
        { $group: { _id: null, avg: { $avg: '$rating' }, count: { $sum: 1 } } },
      ]);
      product.ratingAverage = stats[0]?.avg || 0;
      product.ratingCount = stats[0]?.count || 0;
      await product.save();

      res.status(201).json(review);
    } catch (err) {
      next(err);
    }
  }
);

// =======================================================================
// ADMIN CRUD
// =======================================================================

const productSchema = Joi.object({
  name: Joi.string().max(150).required(),
  description: Joi.string().max(3000).allow(''),
  category: Joi.string().max(60).required(),
  price: Joi.number().min(0).required(),
  discountPercent: Joi.number().min(0).max(95).default(0),
  images: Joi.array().items(Joi.string().uri({ allowRelative: true })).default([]),
  stockQuantity: Joi.number().integer().min(0).default(0),
  isPromoted: Joi.boolean().default(false),
  promotionWeight: Joi.number().min(0).max(10).default(0),
  promotionEndsAt: Joi.date().allow(null),
  isActive: Joi.boolean().default(true),
});

router.post('/', requireAuth, requireRole('admin', 'superadmin'), validateBody(productSchema), async (req, res, next) => {
  try {
    const product = await Product.create({ ...req.body, createdByAdmin: req.user._id });
    res.status(201).json(product);
  } catch (err) {
    next(err);
  }
});

router.put(
  '/:id',
  requireAuth,
  requireRole('admin', 'superadmin'),
  validateBody(productSchema.fork(['name', 'category', 'price'], (s) => s.optional())),
  async (req, res, next) => {
    try {
      const product = await Product.findByIdAndUpdate(req.params.id, req.body, { new: true });
      if (!product) return res.status(404).json({ error: 'Product not found' });
      res.json(product);
    } catch (err) {
      next(err);
    }
  }
);

router.delete('/:id', requireAuth, requireRole('admin', 'superadmin'), async (req, res, next) => {
  try {
    // Soft delete — preserves order history referencing this product
    const product = await Product.findByIdAndUpdate(req.params.id, { isActive: false }, { new: true });
    if (!product) return res.status(404).json({ error: 'Product not found' });
    res.json({ message: 'Product deactivated' });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
