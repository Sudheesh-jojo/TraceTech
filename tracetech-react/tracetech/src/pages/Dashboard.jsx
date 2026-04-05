import React, { useState, useEffect } from 'react';
import { getTodayForecast, getImpactSummary, getWeather } from '../api/api';
import { ConfBadge, Loading, LineChartWidget, HBarChartWidget, FilterTabs } from '../components/Shared';

const STALL_MAP = { Snacks: 1, Juices: 2, 'South Indian': 3, Lunch: 4 };
const TABS = ['All', 'Snacks', 'Juices', 'South Indian', 'Lunch'];

const WEATHER_DATA = {
  0: {
    cls: 'clear',
    icon: 'SUN',
    items: ['Fresh juices', 'Cut fruits', 'Cold beverages'],
    message: 'Clear weather -- cold drinks and fresh items in demand',
  },
  1: {
    cls: 'cloudy',
    icon: 'CLD',
    items: ['Filter Coffee', 'Ginger Tea'],
    message: 'Cloudy weather -- warm beverages seeing higher demand',
  },
  2: {
    cls: 'rainy',
    icon: 'RN',
    items: ['Filter Coffee', 'Ginger Tea', 'Samosa', 'Medhu Vadai'],
    message: 'Rainy day -- comfort food and hot beverages boosted by 10%',
  },
  3: {
    cls: 'stormy',
    icon: 'STM',
    items: [],
    message: 'Stormy weather -- expect 30% lower footfall today',
  },
};

export default function Dashboard({ onNavigate }) {
  const [forecasts, setForecasts] = useState([]);
  const [impact, setImpact]       = useState(null);
  const [weather, setWeather]     = useState(null);
  const [tab, setTab]             = useState('All');
  const [loading, setLoading]     = useState(true);
  const [alertDismissed, setAlertDismissed] = useState(false);

  useEffect(() => {
    Promise.all([getTodayForecast(), getImpactSummary(), getWeather().catch(() => null)])
      .then(([f, i, w]) => { setForecasts(f); setImpact(i); setWeather(w); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  const filtered =
    tab === 'All' ? forecasts : forecasts.filter((f) => f.stallId === STALL_MAP[tab]);

  const totalPredicted = forecasts.reduce((s, f) => s + f.predictedQty, 0);

  // Alert banner logic
  const flaggedItems = forecasts.filter(
    (f) => f.anomalyFlag === true || f.confidence === 'Low'
  );
  const showAlert = flaggedItems.length > 0 && !alertDismissed;

  // Weather condition
  const wc = weather?.weatherCondition ?? weather?.condition ?? 0;
  const wd = WEATHER_DATA[wc] || WEATHER_DATA[0];

  // Build chart data from available impact data
  const chartLabels = ['Week 1', 'Week 2', 'Week 3', 'Week 4'];
  const totalWaste = impact?.totalWasteInr || 0;
  const totalBaseline = totalWaste + (impact?.vsBaselineInr || 0);
  const wasteData    = [totalWaste * 0.20, totalWaste * 0.25, totalWaste * 0.30, totalWaste * 0.25].map(Math.round);
  const baselineData = [totalBaseline * 0.20, totalBaseline * 0.25, totalBaseline * 0.30, totalBaseline * 0.25].map(Math.round);

  return (
    <div className="content">
      {loading ? (
        <Loading text="Loading dashboard..." />
      ) : (
        <>
          {/* -- Urgent Alerts Banner -- */}
          {showAlert && (
            <div className="card" style={{ marginBottom: 16 }}>
              <div className="card-title" style={{ color: '#ef4444' }}>
      ⚠️ Alerts
    </div>

    <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 10 }}>
      {flaggedItems.length} item{flaggedItems.length > 1 ? 's' : ''} flagged today
    </div>

    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      {flaggedItems.slice(0, 5).map((f) => (
        <span key={f.itemId} className="weather-item-tag" style={{ background: '#fee2e2', color: '#991b1b' }}>
          {f.itemName}
        </span>
      ))}
    </div>
  </div>
)}

          {/* -- KPI Cards -- */}
          <div className="kpi-grid">
            <div className="kpi-card green">
              <div className="kpi-label">Predicted Today</div>
              <div className="kpi-value">{totalPredicted.toLocaleString()}</div>
              <div className="kpi-sub">{forecasts.length} items forecasted</div>
            </div>
            <div className="kpi-card blue">
              <div className="kpi-label">Forecast Accuracy</div>
              <div className="kpi-value">{impact?.forecastAccuracy}%</div>
              <div className="kpi-sub">{impact?.avgMape}% avg MAPE</div>
            </div>
            <div className="kpi-card orange">
              <div className="kpi-label">Waste Reduction</div>
              <div className="kpi-value">{impact?.wasteReductionPct}%</div>
              <div className="kpi-sub">vs old method</div>
            </div>
            <div className="kpi-card red">
              <div className="kpi-label">Saved vs Baseline</div>
              <div className="kpi-value">Rs.{impact?.vsBaselineInr?.toLocaleString()}</div>
              <div className="kpi-sub">this month</div>
            </div>
          </div>

          {/* -- Weather Impact Banner -- */}
          <div className="card" style={{ marginBottom: 16 }}>
  <div className="card-title">Weather Impact</div>

  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
    
    <div>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 8 }}>
        {wd.message}
      </div>

      {wd.items.length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {wd.items.map((item) => (
            <span key={item} className="weather-item-tag">
              {item}
            </span>
          ))}
        </div>
      )}
    </div>

    <div style={{ textAlign: 'right' }}>
      <div style={{ fontSize: 18, fontWeight: 700 }}>
        {weather?.temperature ?? '--'}°C
      </div>
      <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
        {wd.icon}
      </div>
    </div>

  </div>
</div>

          {/* -- Charts Row -- */}
          <div className="charts-row">
            <div className="card">
              <div className="card-title">Waste trend -- this month</div>
              <LineChartWidget
                labels={chartLabels}
                datasets={[
                  {
                    label: 'TraceTech',
                    data: wasteData,
                    borderColor: '#10b981',
                    backgroundColor: 'rgba(16,185,129,0.08)',
                    tension: 0.4,
                    fill: true,
                    pointRadius: 3,
                  },
                  {
                    label: 'Baseline',
                    data: baselineData,
                    borderColor: '#ef4444',
                    borderDash: [5, 5],
                    backgroundColor: 'transparent',
                    tension: 0.4,
                    fill: false,
                    pointRadius: 2,
                  },
                ]}
                height={180}
              />
            </div>

            <div className="card">
              <div className="card-title">Top 5 wasted items</div>
              <HBarChartWidget
                labels={impact?.topWastedItems?.map((t) => t.itemName) || []}
                data={impact?.topWastedItems?.map((t) => t.totalWasteCost) || []}
                height={180}
              />
            </div>
          </div>

          {/* -- Forecast Table -- */}
          <div className="card">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
              <span className="card-title" style={{ marginBottom: 0 }}>Today's forecast</span>
            </div>
            <FilterTabs tabs={TABS} active={tab} onChange={setTab} />
            <div style={{ overflowX: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Stall</th>
                    <th>Predicted</th>
                    <th>Range</th>
                    <th>Top Reason</th>
                    <th>Confidence</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((f) => (
                    <tr key={f.itemId}>
                      <td className="fw6">
                        {f.anomalyFlag && <span className="anomaly-dot" title="Anomaly detected" />}
                        {f.itemName}
                      </td>
                      <td className="fs12" style={{ color: 'var(--text-muted)' }}>{['','Snacks','Juices','South Indian','Lunch'][f.stallId]}</td>
                      <td className="fw7 mono">{f.predictedQty}</td>
                      <td className="fs11 mono" style={{ color: 'var(--text-muted)' }}>
                        {f.rangeLow}-{f.rangeHigh}
                      </td>
                      <td className="fs11" style={{ color: 'var(--text-secondary)' }}>
                        {f.topReasons?.[0] || '--'}
                      </td>
                      <td><ConfBadge val={f.confidence} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
