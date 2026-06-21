const express = require('express');
const router = express.Router();
const Joi = require('joi');
const mongoose = require('mongoose');
const crypto = require('crypto');

const { requireAuth, requireRole } = require('../middleware/auth');
const { validateBody } = require('../middleware/validate');
const Product = require('../models/Product');
const Order = require('../models/Order');
const WalletTransaction = require('../models/WalletTransaction');
const walletService = require('../services/walletService');

function httpError(statusCode, message) {
  const err = new Error(message);
  err.statusCode = statusCode;
  return err;
}

// ---------------------------------------------------------------------
// POST /api/orders — checkout from wallet balance. Atomic: stock + wallet + order
// all succeed together or all roll back.
// ---------------------------------------------------------------------
const checkoutSchema = Joi.object({
  items: Joi.array()
    .items(Joi.object({ productId: Joi.string().required(), quantity: Joi.number().integer().min(1).required() }))
    .min(1)
    .required(),
  shippingAddress: Joi.object({
    fullName: Joi.string().required(),
    phone: Joi.string().required(),
    addressLine1: Joi.string().required(),
    addressLine2: Joi.string().allow(''),
    city: Joi.string().required(),
    state: Joi.string().required(),
    country: Joi.string().required(),
  }).required(),
});

router.post('/', requireAuth, validateBody(checkoutSchema), async (req, res, next) => {
  const session = await mongoose.startSession();
  try {
    let order;
    await session.withTransaction(async () => {
      const { items, shippingAddress } = req.body;

      const productIds = items.map((i) => i.productId);
      const products = await Product.find({ _id: { $in: productIds }, isActive: true }).session(session);
      const productMap = new Map(products.map((p) => [p._id.toString(), p]));

      const orderItems = [];
      let totalAmount = 0;

      for (const item of items) {
        const product = productMap.get(item.productId);
        if (!product) throw httpError(404, `Product ${item.productId} not found or unavailable`);
        if (product.stockQuantity < item.quantity) {
          throw httpError(400, `Insufficient stock for "${product.name}" (available: ${product.stockQuantity})`);
        }

        const effectivePrice = Math.round(product.price * (1 - (product.discountPercent || 0) / 100) * 100) / 100;
        orderItems.push({
          product: product._id,
          nameSnapshot: product.name,
          priceSnapshot: effectivePrice,
          quantity: item.quantity,
        });
        totalAmount += effectivePrice * item.quantity;

        product.stockQuantity -= item.quantity;
        product.salesCount += item.quantity;
        await product.save({ session });
      }

      totalAmount = Math.round(totalAmount * 100) / 100;

      const createdOrders = await Order.create(
        [
          {
            user: req.user._id,
            items: orderItems,
            totalAmount,
            shippingAddress,
            status: 'PENDING_PAYMENT',
          },
        ],
        { session }
      );
      order = createdOrders[0];

      // Debit wallet within the SAME transaction session so it can never go out of sync with the order
      const internalReference = `order-${order._id}-${crypto.randomBytes(3).toString('hex')}`;
      const wallet = await walletService.getOrCreateWallet(req.user._id, session);
      if (wallet.isFrozen) throw httpError(403, 'Wallet is frozen');
      if (wallet.balance < totalAmount) {
        throw httpError(402, `Insufficient wallet balance. Required: ${totalAmount}, available: ${wallet.balance}`);
      }

      wallet.balance = Math.round((wallet.balance - totalAmount) * 100) / 100;
      await wallet.save({ session });

      const txDocs = await WalletTransaction.create(
        [
          {
            wallet: wallet._id,
            user: req.user._id,
            type: 'ORDER_PAYMENT',
            amount: totalAmount,
            currency: wallet.currency,
            balanceAfter: wallet.balance,
            status: 'SUCCESS',
            internalReference,
            relatedOrder: order._id,
            description: `Payment for order ${order._id}`,
          },
        ],
        { session }
      );

      order.status = 'PAID';
      order.walletTransaction = txDocs[0]._id;
      order.trackingEvents.push({ status: 'PAID', note: 'Payment confirmed from wallet balance' });
      await order.save({ session });
    });

    res.status(201).json(order);
  } catch (err) {
    if (err.statusCode) return res.status(err.statusCode).json({ error: err.message });
    next(err);
  } finally {
    session.endSession();
  }
});

// ---------------------------------------------------------------------
// GET /api/orders — current user's own orders
// ---------------------------------------------------------------------
router.get('/', requireAuth, async (req, res, next) => {
  try {
    const orders = await Order.find({ user: req.user._id }).sort({ createdAt: -1 });
    res.json(orders);
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// GET /api/orders/:id/tracking — owner or admin only
// ---------------------------------------------------------------------
router.get('/:id/tracking', requireAuth, async (req, res, next) => {
  try {
    const order = await Order.findById(req.params.id);
    if (!order) return res.status(404).json({ error: 'Order not found' });
    const isOwner = String(order.user) === String(req.user._id);
    const isAdmin = ['admin', 'superadmin'].includes(req.user.role);
    if (!isOwner && !isAdmin) return res.status(403).json({ error: 'Not authorized to view this order' });

    res.json({ status: order.status, trackingEvents: order.trackingEvents });
  } catch (err) {
    next(err);
  }
});

// ---------------------------------------------------------------------
// PUT /api/orders/:id/status — admin only, appends a tracking event
// ---------------------------------------------------------------------
router.put(
  '/:id/status',
  requireAuth,
  requireRole('admin', 'superadmin'),
  validateBody(
    Joi.object({
      status: Joi.string().valid('PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED').required(),
      note: Joi.string().max(300).allow(''),
    })
  ),
  async (req, res, next) => {
    try {
      const order = await Order.findById(req.params.id);
      if (!order) return res.status(404).json({ error: 'Order not found' });

      order.status = req.body.status;
      order.trackingEvents.push({ status: req.body.status, note: req.body.note || '' });
      await order.save();

      res.json(order);
    } catch (err) {
      next(err);
    }
  }
);

module.exports = router;
