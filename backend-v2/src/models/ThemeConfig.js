const mongoose = require('mongoose');

// One complete set of design tokens (used for both 'light' and 'dark' variants)
const colorTokenSchema = new mongoose.Schema(
  {
    primary: { type: String, default: '#E53935' },
    primaryVariant: { type: String, default: '#B71C1C' },
    secondary: { type: String, default: '#FFB300' },
    background: { type: String, default: '#FFFFFF' },
    surface: { type: String, default: '#F5F5F5' },
    onPrimary: { type: String, default: '#FFFFFF' },
    onBackground: { type: String, default: '#1A1A1A' },
    onSurface: { type: String, default: '#1A1A1A' },
    success: { type: String, default: '#2E7D32' },
    warning: { type: String, default: '#F9A825' },
    error: { type: String, default: '#C62828' },
    border: { type: String, default: '#E0E0E0' },
  },
  { _id: false }
);

const themeConfigSchema = new mongoose.Schema(
  {
    singletonKey: { type: String, default: 'GLOBAL_THEME', unique: true }, // enforces a single document
    brandName: { type: String, default: 'Connexa' },
    logoUrl: { type: String, default: null },
    faviconUrl: { type: String, default: null },

    light: { type: colorTokenSchema, default: () => ({}) },
    dark: { type: colorTokenSchema, default: () => ({}) },

    defaultMode: { type: String, enum: ['light', 'dark', 'system'], default: 'system' },
    allowUserToggle: { type: Boolean, default: true },

    // Bumped on every save. Frontend polls/sockets on this to know when to refetch & re-render.
    version: { type: Number, default: 1 },

    updatedByAdmin: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null },
  },
  { timestamps: true }
);

themeConfigSchema.pre('save', function bumpVersion(next) {
  if (!this.isNew) this.version += 1;
  next();
});

module.exports = mongoose.model('ThemeConfig', themeConfigSchema);
