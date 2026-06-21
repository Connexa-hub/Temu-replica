const express = require('express');
const router = express.Router();
const Joi = require('joi');

const { requireAuth, requireRole } = require('../middleware/auth');
const { validateBody } = require('../middleware/validate');
const { upload, storeBannerImage, storeBrandImage } = require('../services/uploadService');
const ThemeConfig = require('../models/ThemeConfig');
const Banner = require('../models/Banner');
const User = require('../models/User');
const walletService = require('../services/walletService');

// Every route below requires an authenticated admin or superadmin.
router.use(requireAuth, requireRole('admin', 'superadmin'));

// =======================================================================
// THEME ENGINE — admin edits propagate to frontend via version bump
// =======================================================================

const colorTokenSchema = Joi.object({
  primary: Joi.string(),
  primaryVariant: Joi.string(),
  secondary: Joi.string(),
  background: Joi.string(),
  surface: Joi.string(),
  onPrimary: Joi.string(),
  onBackground: Joi.string(),
  onSurface: Joi.string(),
  success: Joi.string(),
  warning: Joi.string(),
  error: Joi.string(),
  border: Joi.string(),
}).min(1);

const updateThemeSchema = Joi.object({
  brandName: Joi.string().max(60),
  light: colorTokenSchema,
  dark: colorTokenSchema,
  defaultMode: Joi.string().valid('light', 'dark', 'system'),
  allowUserToggle: Joi.boolean(),
}).min(1);

router.get('/theme', async (req, res, next) => {
  try {
    let theme = await ThemeConfig.findOne({ singletonKey: 'GLOBAL_THEME' });
    if (!theme) theme = await ThemeConfig.create({});
    res.json(theme);
  } catch (err) {
    next(err);
  }
});

router.put('/theme', validateBody(updateThemeSchema), async (req, res, next) => {
  try {
    let theme = await ThemeConfig.findOne({ singletonKey: 'GLOBAL_THEME' });
    if (!theme) theme = new ThemeConfig({});

    const { brandName, light, dark, defaultMode, allowUserToggle } = req.body;
    if (brandName !== undefined) theme.brandName = brandName;
    if (light) Object.assign(theme.light, light);
    if (dark) Object.assign(theme.dark, dark);
    if (defaultMode !== undefined) theme.defaultMode = defaultMode;
    if (allowUserToggle !== undefined) theme.allowUserToggle = allowUserToggle;
    theme.updatedByAdmin = req.user._id;

    await theme.save(); // pre-save hook bumps `version` automatically
    res.json({ message: 'Theme updated and will propagate to clients immediately', theme });
  } catch (err) {
    next(err);
  }
});

// Logo / favicon upload — processed through sharp, never raw-saved
router.post('/theme/logo', upload.single('image'), async (req, res, next) => {
  try {
    if (!req.file) return res.status(400).json({ error: 'No image file provided (field name: image)' });
    const { url, previewUrl } = await storeBrandImage(req.file.buffer);

    let theme = await ThemeConfig.findOne({ singletonKey: 'GLOBAL_THEME' });
    if (!theme) theme = new ThemeConfig({});
    theme.logoUrl = url;
    theme.updatedByAdmin = req.user._id;
    await theme.save();

    res.json({ message: 'Logo updated', logoUrl: url, previewUrl, theme });
  } catch (err) {
    next(err);
  }
});

// =======================================================================
// BANNER / CAROUSEL CMS
// =======================================================================

const bannerCreateSchema = Joi.object({
  title: Joi.string().max(120).required(),
  subtitle: Joi.string().max(200).allow(''),
  linkType: Joi.string().valid('PRODUCT', 'CATEGORY', 'EXTERNAL_URL', 'NONE').default('NONE'),
  linkValue: Joi.string().allow(null, ''),
  placement: Joi.string().valid('HOME_CAROUSEL', 'HOME_PROMO_STRIP', 'CATEGORY_TOP', 'FLASH_SALE').default('HOME_CAROUSEL'),
  sortOrder: Joi.number().integer().default(0),
  isActive: Joi.boolean().default(true),
  startsAt: Joi.date().allow(null),
  endsAt: Joi.date().allow(null),
  targetCountries: Joi.array().items(Joi.string().length(2).uppercase()).default([]),
});

router.get('/banners', async (req, res, next) => {
  try {
    const banners = await Banner.find().sort({ placement: 1, sortOrder: 1 });
    res.json(banners);
  } catch (err) {
    next(err);
  }
});

// Create banner with image upload in one request (multipart/form-data)
router.post(
  '/banners',
  upload.fields([{ name: 'image', maxCount: 1 }, { name: 'imageDark', maxCount: 1 }]),
  async (req, res, next) => {
    try {
      const { error, value } = bannerCreateSchema.validate(req.body, { abortEarly: false, stripUnknown: true });
      if (error) return res.status(400).json({ error: 'Validation failed', details: error.details.map((d) => d.message) });

      const imageFile = req.files?.image?.[0];
      if (!imageFile) return res.status(400).json({ error: 'A banner image is required (field name: image)' });

      const { url } = await storeBannerImage(imageFile.buffer);
      let darkUrl = null;
      if (req.files?.imageDark?.[0]) {
        const darkResult = await storeBannerImage(req.files.imageDark[0].buffer);
        darkUrl = darkResult.url;
      }

      const banner = await Banner.create({
        ...value,
        imageUrl: url,
        imageUrlDark: darkUrl,
        createdByAdmin: req.user._id,
      });

      res.status(201).json(banner);
    } catch (err) {
      next(err);
    }
  }
);

const bannerUpdateSchema = bannerCreateSchema.fork(['title'], (s) => s.optional()).min(1);

router.put(
  '/banners/:id',
  upload.fields([{ name: 'image', maxCount: 1 }, { name: 'imageDark', maxCount: 1 }]),
  async (req, res, next) => {
    try {
      const { error, value } = bannerUpdateSchema.validate(req.body, { abortEarly: false, stripUnknown: true });
      if (error) return res.status(400).json({ error: 'Validation failed', details: error.details.map((d) => d.message) });

      const banner = await Banner.findById(req.params.id);
      if (!banner) return res.status(404).json({ error: 'Banner not found' });

      Object.assign(banner, value);

      if (req.files?.image?.[0]) {
        const { url } = await storeBannerImage(req.files.image[0].buffer);
        banner.imageUrl = url;
      }
      if (req.files?.imageDark?.[0]) {
        const { url } = await storeBannerImage(req.files.imageDark[0].buffer);
        banner.imageUrlDark = url;
      }

      await banner.save();
      res.json(banner);
    } catch (err) {
      next(err);
    }
  }
);

router.delete('/banners/:id', async (req, res, next) => {
  try {
    const banner = await Banner.findByIdAndDelete(req.params.id);
    if (!banner) return res.status(404).json({ error: 'Banner not found' });
    res.json({ message: 'Banner deleted' });
  } catch (err) {
    next(err);
  }
});

// Bulk reorder — admin drags carousel items into a new order on the frontend
router.put(
  '/banners-reorder',
  validateBody(Joi.object({ orderedIds: Joi.array().items(Joi.string()).min(1).required() })),
  async (req, res, next) => {
    try {
      const { orderedIds } = req.body;
      await Promise.all(orderedIds.map((id, index) => Banner.updateOne({ _id: id }, { sortOrder: index })));
      res.json({ message: 'Order updated' });
    } catch (err) {
      next(err);
    }
  }
);

// =======================================================================
// WALLET ADMIN — manual adjustments, fully audited via WalletTransaction
// =======================================================================

router.post(
  '/wallet/adjust',
  validateBody(
    Joi.object({
      userId: Joi.string().required(),
      amount: Joi.number().required(), // positive = credit, negative = debit
      reason: Joi.string().max(300).required(),
    })
  ),
  async (req, res, next) => {
    try {
      const { userId, amount, reason } = req.body;
      const tx = await walletService.adminAdjustBalance({ userId, amount, adminUserId: req.user._id, reason });
      res.json({ message: 'Wallet adjusted', transaction: tx });
    } catch (err) {
      if (err.code) return res.status(400).json({ error: err.message, code: err.code });
      next(err);
    }
  }
);

// =======================================================================
// USER / ROLE MANAGEMENT — role escalation requires superadmin, to prevent admin self-escalation chains
// =======================================================================

router.get('/users', async (req, res, next) => {
  try {
    const { search, page = 1, limit = 50 } = req.query;
    const filter = search
      ? { $or: [{ email: new RegExp(search, 'i') }, { name: new RegExp(search, 'i') }] }
      : {};
    const users = await User.find(filter)
      .sort({ createdAt: -1 })
      .skip((page - 1) * limit)
      .limit(Number(limit));
    res.json(users.map((u) => u.toSafeJSON()));
  } catch (err) {
    next(err);
  }
});

router.put(
  '/users/:id/role',
  requireRole('superadmin'),
  validateBody(Joi.object({ role: Joi.string().valid('user', 'admin', 'superadmin').required() })),
  async (req, res, next) => {
    try {
      const user = await User.findById(req.params.id);
      if (!user) return res.status(404).json({ error: 'User not found' });
      user.role = req.body.role;
      user.refreshTokenVersion += 1; // force re-login so the new role takes effect on next token issue
      await user.save();
      res.json({ message: 'Role updated', user: user.toSafeJSON() });
    } catch (err) {
      next(err);
    }
  }
);

router.put('/users/:id/deactivate', async (req, res, next) => {
  try {
    const user = await User.findById(req.params.id);
    if (!user) return res.status(404).json({ error: 'User not found' });
    if (user.role === 'superadmin' && req.user.role !== 'superadmin') {
      return res.status(403).json({ error: 'Only a superadmin can deactivate a superadmin account' });
    }
    user.isActive = false;
    user.refreshTokenVersion += 1;
    await user.save();
    res.json({ message: 'User deactivated' });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
