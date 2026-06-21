const Joi = require('joi');

const register = Joi.object({
  email: Joi.string().email().required(),
  password: Joi.string().min(8).max(72).required(),
  name: Joi.string().min(2).max(80).required(),
  phoneNumber: Joi.string().max(20).allow('', null),
  referralCode: Joi.string().max(40).allow('', null),
});

const login = Joi.object({
  email: Joi.string().email().required(),
  password: Joi.string().required(),
});

const refresh = Joi.object({
  refreshToken: Joi.string().required(),
});

const forgotPassword = Joi.object({
  email: Joi.string().email().required(),
});

const resetPassword = Joi.object({
  email: Joi.string().email().required(),
  otp: Joi.string().length(6).required(),
  newPassword: Joi.string().min(8).max(72).required(),
});

const verifyOtp = Joi.object({
  email: Joi.string().email().required(),
  otp: Joi.string().length(6).required(),
});

module.exports = { register, login, refresh, forgotPassword, resetPassword, verifyOtp };
