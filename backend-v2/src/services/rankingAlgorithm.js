/**
 * Product ranking algorithm for the homepage carousel / "recommended for you" rails.
 *
 * This is a transparent, explainable weighted-scoring model — not a black box, and not
 * hardcoded/static ordering. Admins can see and tune the weights via /api/admin/algorithm-weights.
 *
 * Score = (salesVelocity * wSales) + (ratingScore * wRating) + (recency * wRecency) + (promotion * wPromotion)
 *
 * All inputs are normalized to a 0-1 range before weighting so no single signal dominates
 * just because of its raw scale (e.g. salesCount=10000 vs ratingAverage=5).
 */

const DEFAULT_WEIGHTS = {
  sales: 0.35,
  rating: 0.25,
  recency: 0.15,
  promotion: 0.25,
};

function normalize(value, max) {
  if (!max || max <= 0) return 0;
  return Math.min(value / max, 1);
}

function scoreProducts(products, weights = DEFAULT_WEIGHTS) {
  if (products.length === 0) return [];

  const maxSales = Math.max(...products.map((p) => p.salesCount || 0), 1);
  const now = Date.now();
  const maxAgeMs = 1000 * 60 * 60 * 24 * 90; // 90-day recency window

  const scored = products.map((p) => {
    const salesNorm = normalize(p.salesCount || 0, maxSales);
    const ratingNorm = (p.ratingAverage || 0) / 5;
    const ageMs = now - new Date(p.createdAt).getTime();
    const recencyNorm = Math.max(0, 1 - ageMs / maxAgeMs);

    let promotionNorm = 0;
    if (p.isPromoted && (!p.promotionEndsAt || new Date(p.promotionEndsAt) > new Date())) {
      promotionNorm = Math.min((p.promotionWeight || 1) / 10, 1);
    }

    const score =
      salesNorm * weights.sales +
      ratingNorm * weights.rating +
      recencyNorm * weights.recency +
      promotionNorm * weights.promotion;

    return { product: p, score, breakdown: { salesNorm, ratingNorm, recencyNorm, promotionNorm } };
  });

  scored.sort((a, b) => b.score - a.score);
  return scored;
}

module.exports = { scoreProducts, DEFAULT_WEIGHTS };
