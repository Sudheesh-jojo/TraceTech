import axios from 'axios';
 
const BASE_URL = 'http://localhost:8080';
 
const API = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080'
});
 
// Attach JWT token to every request automatically
API.interceptors.request.use((config) => {
  const token = localStorage.getItem('tt_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
 
// ── AUTH ───────────────────────────────────────────────────────
export const login = (email, password) =>
  API.post('/api/auth/login', { email, password }).then((r) => r.data);
 
// ── FORECAST ───────────────────────────────────────────────────
export const getTodayForecast = () =>
  API.get('/api/forecast/today').then((r) => r.data);
 
export const getTomorrowForecast = () =>
  API.get('/api/forecast/tomorrow').then((r) => r.data);
 
export const getItemForecast = (itemId) =>
  API.get(`/api/forecast/item/${itemId}`).then((r) => r.data);
 
export const simulateForecast = (params) =>
  API.post('/api/forecast/simulate', params).then((r) => r.data);
 
// ── IMPACT ─────────────────────────────────────────────────────
// Returns: {
//   forecastAccuracy, avgMape, wasteReductionPct, vsBaselineInr,
//   totalRevenue, totalWasteInr, totalProfit, totalDays,
//   avgDailyWasteInr, avgDailyRevenue, totalWasteQty,
//   topWastedItems: [{ itemName, totalWasteQty, totalWasteCost }]
// }
export const getImpactSummary = (from = '', to = '') =>
  API.get('/api/impact/summary', { params: { from, to } }).then((r) => r.data);
 
// Returns: [{ date: "2026-04-01", waste: 22.0, baseline: 99.5 }, ...]
export const getDailyWaste = (from = '', to = '') =>
  API.get('/api/impact/daily', { params: { from, to } }).then((r) => r.data);
 
// ── MENU ───────────────────────────────────────────────────────
export const getMenuItems = () =>
  API.get('/api/menu/items').then((r) => r.data);
 
// Returns: { 1: [...], 2: [...], 3: [...], 4: [...] }
export const getMenuByStall = () =>
  API.get('/api/menu/items/by-stall').then((r) => r.data);
 
// ── SALES ──────────────────────────────────────────────────────
// Body: [{ itemId, saleDate, qtyPrepared, qtySold }]
export const submitBulkSales = (salesArray) =>
  API.post('/api/sales/submit/bulk', salesArray).then((r) => r.data);
 
// ── WEATHER ────────────────────────────────────────────────────
export const getWeather = () =>
  API.get('/api/weather/today').then((r) => r.data);
 
// ── PROCUREMENT ────────────────────────────────────────────────
// Body: { menuItemId: predictedQty, ... }
export const getProcurementPlan = (qtyMap) =>
  API.post('/api/procurement/plan', qtyMap).then((r) => r.data);

export const getProcurementPlanToday = () =>
  API.get('/api/procurement/plan/today').then((r) => r.data);
 
// ── PRODUCTION ─────────────────────────────────────────────────
// Body: { menuItemId: predictedQty, ... }
export const getProductionPlan = (qtyMap) =>
  API.post('/api/production/plan', qtyMap).then((r) => r.data);
 
// ── INVENTORY ──────────────────────────────────────────────────
export const getInventory = () =>
  API.get('/api/inventory').then((r) => r.data);
 
// ── FEEDBACK (Step 5) ──────────────────────────────────────────
// Body: { date: "2026-04-14", actuals: { menuItemId: qtySold, ... } }
export const submitEndOfDay = (date, actuals) =>
  API.post('/api/feedback/end-of-day', { date, actuals }).then((r) => r.data);

export const getDailyAccuracy = (date) =>
  API.get('/api/feedback/accuracy', { params: { date } }).then((r) => r.data);

export const getRollingMape = () =>
  API.get('/api/feedback/mape/rolling').then((r) => r.data);

export const getMapeTrend = (since) =>
  API.get('/api/feedback/mape/trend', { params: { since } }).then((r) => r.data);