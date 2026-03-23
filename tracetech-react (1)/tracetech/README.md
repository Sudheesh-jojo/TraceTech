# TraceTech Frontend — RIT Canteen AI Forecasting

React.js frontend for the TraceTech canteen waste reduction and demand forecasting system.

---

## Project Structure

```
tracetech/
├── public/
│   └── index.html
├── src/
│   ├── api/
│   │   └── api.js          ← ALL backend API calls live here
│   ├── components/
│   │   └── Shared.jsx      ← Reusable components (charts, badges, spinner)
│   ├── pages/
│   │   ├── LoginPage.jsx
│   │   ├── Dashboard.jsx
│   │   ├── DemandPrediction.jsx
│   │   ├── PrepPlan.jsx
│   │   ├── DynamicPricing.jsx
│   │   ├── ProfitWaste.jsx
│   │   ├── StrategySimulation.jsx
│   │   └── SalesInput.jsx
│   ├── App.jsx             ← Sidebar layout + page routing
│   ├── index.js
│   └── index.css           ← All global styles
└── package.json
```

---

## Quick Start

```bash
npm install
npm start
```

App runs at http://localhost:3000

---

## Connecting to Backend

### Step 1 — Set the base URL
Open `src/api/api.js` and change line 5:

```js
const BASE_URL = 'http://localhost:8080';  // ← change this to your backend URL
```

### Step 2 — Check all endpoints

All API calls are in `src/api/api.js`. Here is a summary:

| Function            | Method | Endpoint                        | Used in               |
|---------------------|--------|---------------------------------|-----------------------|
| `login`             | POST   | `/api/auth/login`               | LoginPage             |
| `getTodayForecast`  | GET    | `/api/forecast/today`           | Dashboard, Demand, Prep, Pricing, Sales |
| `getItemForecast`   | GET    | `/api/forecast/item/:id`        | DemandPrediction      |
| `simulateForecast`  | POST   | `/api/forecast/simulate`        | StrategySimulation    |
| `getImpactSummary`  | GET    | `/api/analytics/impact`         | Dashboard, ProfitWaste|
| `getMenuItems`      | GET    | `/api/menu/items`               | DynamicPricing        |
| `getMenuByStall`    | GET    | `/api/menu/by-stall`            | SalesInput            |
| `submitBulkSales`   | POST   | `/api/sales/bulk`               | SalesInput            |

### Step 3 — JWT Auth
The Axios interceptor in `api.js` automatically attaches the token:
```
Authorization: Bearer <token>
```
The token is stored in `localStorage` as `tt_token` after login.

---

## Expected API Response Shapes

### `GET /api/forecast/today`
```json
[
  {
    "itemId": 1,
    "itemName": "Samosa",
    "stallId": 1,
    "stallName": "Snacks",
    "predictedQty": 118,
    "rangeLow": 103,
    "rangeHigh": 132,
    "topReasons": ["7-day avg: 100 units", "Friday trend up", "Weather: Clear"],
    "confidence": "High",
    "anomalyFlag": false,
    "estimatedCostIfOver": 72,
    "baseDailyQty": 100,
    "cluster": "samosa",
    "sellingPrice": 10,
    "costPrice": 6,
    "mealPeriod": "allday"
  }
]
```

### `GET /api/forecast/item/:id`
```json
[
  { "day": "Mon", "qty": 110 },
  { "day": "Tue", "qty": 95 },
  ...
]
```

### `GET /api/analytics/impact?from=YYYY-MM-DD&to=YYYY-MM-DD`
```json
{
  "forecastAccuracy": 96.2,
  "mape": 4.24,
  "wasteReductionPct": 32.2,
  "vsBaselineInr": 1580,
  "totalRevenue": 284600,
  "totalWasteInr": 12450,
  "totalProfit": 118400,
  "totalDays": 17,
  "avgDailyWaste": 732,
  "avgDailyRevenue": 16741,
  "wasteQtyUnits": 312,
  "dailyWaste": [620, 710, 680, ...],
  "dailyBaseline": [820, 900, 880, ...],
  "dailyDates": ["Mar 1", "Mar 2", ...],
  "topWasted": [
    { "name": "Egg Fried Rice", "waste": 1840 },
    ...
  ]
}
```

### `POST /api/forecast/simulate`
Request body:
```json
{
  "dayOfWeek": "Friday",
  "isExamWeek": true,
  "isHoliday": false,
  "weather": "Rainy",
  "temperature": 32
}
```
Response: Same as `/api/forecast/today` but each item also has `"simulatedQty": 145`

### `POST /api/sales/bulk`
```json
[
  { "itemId": 1, "date": "2026-03-17", "qtyPrepared": 120, "qtySold": 108 }
]
```

---

## Notes for the Backend Developer

- CORS must allow `http://localhost:3000`
- All routes except `/api/auth/login` require `Authorization: Bearer <token>`
- The `cluster` field in forecast/menu items drives the Prep Plan and Dynamic Pricing logic
- `dosa_batter` cluster items always show in the "Tonight" section of Prep Plan
- Item IDs in `BATTER_PER_UNIT` (PrepPlan.jsx) must match your actual item IDs — update them if needed
