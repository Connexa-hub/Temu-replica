const express = require('express');
const router = express.Router();
const Joi = require('joi');

const { validateBody } = require('../middleware/validate');
const { detectCountry, currencyForCountry } = require('../middleware/geo');
const { detectCardBrand } = require('../services/cardBrandService');
const ThemeConfig = require('../models/ThemeConfig');
const Banner = require('../models/Banner');

// ---------------------------------------------------------------------
// POST /api/utility/card-brand  — used by the deposit UI to render a live card face
// Body: { binDigits } — first 6-8 digits ONLY, never accepts/stores a full PAN
// ---------------------------------------------------------------------
const cardBrandSchema = Joi.object({
  binDigits: Joi.string().pattern(/^\d{0,8}$/).required(),
});

router.post('/card-brand', validateBody(cardBrandSchema), (req, res) => {
  const result = detectCardBrand(req.body.binDigits);
  res.json(result);
});

// ---------------------------------------------------------------------
// GET /api/utility/storefront-bootstrap
// Single call the frontend makes on launch: theme tokens (with version for cache-busting),
// active banners (already filtered by schedule + country + sorted), and resolved geo/currency.
// This is the "instant propagation" mechanism — frontend polls/compares `theme.version`
// and refetches automatically when an admin pushes a change.
// ---------------------------------------------------------------------
router.get('/storefront-bootstrap', detectCountry, async (req, res, next) => {
  try {
    let theme = await ThemeConfig.findOne({ singletonKey: 'GLOBAL_THEME' });
    if (!theme) theme = await ThemeConfig.create({});

    const now = new Date();
    const country = req.geo.country;

    const banners = await Banner.find({
      isActive: true,
      $and: [
        { $or: [{ startsAt: null }, { startsAt: { $lte: now } }] },
        { $or: [{ endsAt: null }, { endsAt: { $gte: now } }] },
        { $or: [{ targetCountries: { $size: 0 } }, ...(country ? [{ targetCountries: country }] : [])] },
      ],
    }).sort({ placement: 1, sortOrder: 1 });

    res.json({
      theme,
      banners,
      geo: req.geo,
      currency: currencyForCountry(country),
      serverTime: now.toISOString(),
    });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
