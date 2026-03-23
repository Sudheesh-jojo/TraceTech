import React, { useState, useEffect } from 'react';
import { getTodayForecast, getImpactSummary } from '../api/api';
import { ConfBadge, Loading, LineChartWidget, HBarChartWidget, FilterTabs } from '../components/Shared';

const STALL_MAP = { Snacks: 1, Juices: 2, 'South Indian': 3, Lunch: 4 };
const TABS = ['All', 'Snacks', 'Juices', 'South Indian', 'Lunch'];

export default function Dashboard({ onNavigate }) {
  const [forecasts, setForecasts] = useState([]);
  const [impact, setImpact]       = useState(null);
  const [tab, setTab]             = useState('All');
  const [loading, setLoading]     = useState(true);

  useEffect(() => {
    Promise.all([getTodayForecast(), getImpactSummary()])
      .then(([f, i]) => { setForecasts(f); setImpact(i); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  const filtered =
    tab === 'All' ? forecasts : forecasts.filter((f) => f.stallId === STALL_MAP[tab]);

  const totalPredicted = forecasts.reduce((s, f) => s + f.predictedQty, 0);

  // Build chart data from available impact data
  const chartLabels = ['Week 1', 'Week 2', 'Week 3', 'Week 4']
  const totalWaste = impact?.totalWasteInr || 0
  const totalBaseline = totalWaste + (impact?.vsBaselineInr || 0)
  const wasteData    = [totalWaste * 0.20, totalWaste * 0.25, totalWaste * 0.30, totalWaste * 0.25].map(Math.round)
  const baselineData = [totalBaseline * 0.20, totalBaseline * 0.25, totalBaseline * 0.30, totalBaseline * 0.25].map(Math.round)

  return (
    <div className="content">
      {loading ? (
        <Loading text="Loading dashboard…" />
      ) : (
        <>
          {/* ── KPI Cards ── */}
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
              <div className="kpi-value">₹{impact?.vsBaselineInr?.toLocaleString()}</div>
              <div className="kpi-sub">this month</div>
            </div>
          </div>

          {/* ── Charts Row ── */}
          <div className="charts-row">
            <div className="card">
              <div className="card-title">Waste trend — this month</div>
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

          {/* ── Forecast Table ── */}
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
                        {f.rangeLow}–{f.rangeHigh}
                      </td>
                      <td className="fs11" style={{ color: 'var(--text-secondary)' }}>
                        {f.topReasons?.[0] || '—'}
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
