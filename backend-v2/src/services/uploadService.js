const multer = require('multer');
const sharp = require('sharp');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

const UPLOAD_ROOT = path.join(__dirname, '..', '..', 'uploads');
const BANNERS_DIR = path.join(UPLOAD_ROOT, 'banners');
const BRAND_DIR = path.join(UPLOAD_ROOT, 'brand');

for (const dir of [UPLOAD_ROOT, BANNERS_DIR, BRAND_DIR]) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

const ALLOWED_MIME = new Set(['image/jpeg', 'image/png', 'image/webp']);

const storage = multer.memoryStorage(); // we process with sharp before writing to disk

const upload = multer({
  storage,
  limits: { fileSize: (Number(process.env.MAX_UPLOAD_MB) || 8) * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    if (!ALLOWED_MIME.has(file.mimetype)) {
      return cb(new Error('Only JPEG, PNG, or WEBP images are allowed'));
    }
    cb(null, true);
  },
});

/**
 * Processes an in-memory image buffer into a web-optimized WEBP, plus a small preview/thumbnail,
 * and writes both to disk. Returns relative URLs the API can expose.
 * Using sharp (not just saving the raw upload) is what makes "image preview" and consistent
 * banner dimensions actually real rather than just echoing back whatever the admin uploaded.
 */
async function processAndStoreImage(buffer, { targetDir, baseName, width = 1600 }) {
  const id = crypto.randomBytes(8).toString('hex');
  const fullFileName = `${baseName}-${id}.webp`;
  const previewFileName = `${baseName}-${id}-preview.webp`;

  const fullPath = path.join(targetDir, fullFileName);
  const previewPath = path.join(targetDir, previewFileName);

  await sharp(buffer)
    .resize({ width, withoutEnlargement: true })
    .webp({ quality: 82 })
    .toFile(fullPath);

  await sharp(buffer)
    .resize({ width: 320, withoutEnlargement: true })
    .webp({ quality: 60 })
    .toFile(previewPath);

  const subDir = path.basename(targetDir);
  return {
    url: `/uploads/${subDir}/${fullFileName}`,
    previewUrl: `/uploads/${subDir}/${previewFileName}`,
  };
}

async function storeBannerImage(buffer) {
  return processAndStoreImage(buffer, { targetDir: BANNERS_DIR, baseName: 'banner', width: 1600 });
}

async function storeBrandImage(buffer) {
  return processAndStoreImage(buffer, { targetDir: BRAND_DIR, baseName: 'brand', width: 512 });
}

module.exports = { upload, storeBannerImage, storeBrandImage, UPLOAD_ROOT };
