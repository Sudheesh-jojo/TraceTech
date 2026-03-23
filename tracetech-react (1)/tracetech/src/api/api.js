import axios from 'axios';

const BASE_URL = 'http://localhost:8080';

const API = axios.create({ baseURL: BASE_URL });

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
export const getTomorrowForecast = () =>
  API.get('/api/forecast/tomorrow').then((r) => r.data);