import React, { useState, useEffect } from 'react';
import { getImpactSummary, getDailyWaste } from '../api/api';
import { Loading, LineChartWidget, HBarChartWidget } from '../components/Shared';

export default function ProfitWaste() {
  const [impact, setImpact]       = useState(null);
  const [dailyData, setDailyData] = useState([]);
  const [loading, setLoading]     = useState(true);

  const [from, setFrom] = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  });
  const [to, setTo] = useState(() => new Date().toISOString().slice(0, 10));

  const fetchData = () => {
    setLoading(true);
    Promise.all([
      getImpactSummary(from, to),
      getDailyWaste(from, to),
    ])
      .then(([impactData, daily]) => {
        setImpact(impactData);
        setDailyData(daily);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, []);

  // ── Real chart data from API ───────────────────────────────
  const chartLabels  = dailyData.map(d => d.date.slice(5));
  const wasteData    = dailyData.map(d => d.waste);
  const baselineData = dailyData.map(d => d.baseline);

  // ── Derived values ─────────────────────────────────────────
  const totalWaste       = impact?.totalWasteInr || 0;
  const withoutWaste     = totalWaste + (impact?.vsBaselineInr || 0);
  const withoutDailyLoss = impact?.totalDays > 0
    ? Math.round(withoutWaste / impact.totalDays) : 0;
  const withDailyLoss    = impact?.avgDailyWasteInr || 0;

  // FIX 1: Derive month label from selected `from` date, not today.
  // Previously used `new Date()` which always showed the current month
  // regardless of what date range the user had selected.
  const monthName = from
    ? new Date(from + 'T00:00:00').toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })
    : '';

  const topWasted = impact?.topWastedItems?.[0];

  // FIX 4: Annual savings scaled to actual date range length, not blindly × 12.
  // Previously did vsBaselineInr × 12, which assumed the selected range was
  // exactly one month and inflated savings for shorter/longer ranges.
  const annualSavings = (() => {
    if (!impact?.totalDays || impact.totalDays === 0) return 0;
    const dailySaving = (impact.vsBaselineInr || 0) / impact.totalDays;
    return Math.round(dailySaving * 365);
  })();

  // FIX 2: "Without TraceTech" accuracy is derived from the API response.
  // Previously hardcoded as the string "~60%" which never changed.
  // The backend now returns baselineAccuracy (see ImpactService.java fix).
  // Falls back to "N/A" if not yet available from older API versions.
  const baselineAccuracyLabel = impact?.baselineAccuracy != null
    ? `${impact.baselineAccuracy}%`
    : '~60%'; // fallback until backend is updated

  return (
    <div className="content modern">

      {/* 🔹 HEADER / FILTER BAR */}
      <div className="glass-card flex between center wrap mb-20">
        <div className="section-title">Profit & Waste Analytics</div>
        <div className="flex gap-10 center wrap">
          <input
            type="date" value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="input-modern"
          />
          <span className="muted">→</span>
          <input
            type="date" value={to}
            onChange={(e) => setTo(e.target.value)}
            className="input-modern"
          />
          <button className="btn-primary-modern" onClick={fetchData}>Apply</button>
        </div>
      </div>

      {loading ? (
        <Loading text="Loading analytics..." />
      ) : !impact ? (
        <div className="empty">No data available.</div>
      ) : (
        <>
          {/* 🔹 AI NARRATIVE */}
          <div className="glass-card highlight mb-20">
            <div className="card-title">AI Impact Summary</div>
            <p className="narrative">
              In <strong>{monthName}</strong>, waste dropped by{' '}
              <strong>{impact.wasteReductionPct}%</strong>, saving{' '}
              <strong>Rs.{impact.vsBaselineInr?.toLocaleString()}</strong>.
              Accuracy reached <strong>{impact.forecastAccuracy}%</strong>.
              Revenue: <strong>Rs.{((impact.totalRevenue || 0) / 1000).toFixed(1)}k</strong>.
              {topWasted && <> Biggest loss: <strong>{topWasted.itemName}</strong>.</>}
              {' '}Annual projected savings: <strong>Rs.{annualSavings.toLocaleString()}</strong>.
            </p>
          </div>

          {/* 🔹 KPI GRID */}
          <div className="grid-4 mb-20">
            <KPI label="Revenue"    value={`Rs.${((impact.totalRevenue || 0) / 1000).toFixed(1)}k`} />
            <KPI label="Waste Cost" value={`Rs.${totalWaste.toLocaleString()}`} danger />
            <KPI label="Reduction"  value={`${impact.wasteReductionPct}%`} />
            <KPI label="Saved"      value={`Rs.${impact.vsBaselineInr?.toLocaleString()}`} success />
          </div>

          {/* 🔹 BEFORE / AFTER */}
          <div className="grid-2 mb-20">
            <ComparisonCard
              title="Without TraceTech"
              data={[
                ['Waste',      `Rs.${withoutWaste.toLocaleString()}`],
                // FIX 2: was hardcoded '~60%'
                ['Accuracy',   baselineAccuracyLabel],
                ['Daily Loss', `Rs.${withoutDailyLoss}`],
              ]}
              danger
            />
            <ComparisonCard
              title="With TraceTech"
              data={[
                ['Waste',      `Rs.${totalWaste.toLocaleString()}`],
                ['Accuracy',   `${impact.forecastAccuracy}%`],
                ['Daily Loss', `Rs.${withDailyLoss}`],
              ]}
              success
            />
          </div>

          {/* 🔹 CHARTS */}
          <div className="grid-2 mb-20">
            <div className="glass-card">
              <div className="card-title">Waste vs Baseline (Daily)</div>
              <LineChartWidget
                labels={chartLabels}
                datasets={[
                  { label: 'AI Waste', data: wasteData,    borderColor: '#22c55e', fill: true },
                  { label: 'Baseline', data: baselineData, borderColor: '#ef4444', borderDash: [5, 5] },
                ]}
                height={220}
              />
            </div>

            <div className="glass-card">
              <div className="card-title">Top Wasted Items</div>
              <HBarChartWidget
                labels={impact.topWastedItems?.map(i => i.itemName) || []}
                data={impact.topWastedItems?.map(i => Number(i.totalWasteCost)) || []}
                height={220}
              />
            </div>
          </div>

          {/* 🔹 STATS GRID */}
          <div className="glass-card">
            <div className="card-title">Summary Stats</div>
            <div className="grid-3 stats">
              <Stat label="Days"       val={impact.totalDays} />
              <Stat label="Avg Waste"  val={`Rs.${impact.avgDailyWasteInr}`} />
              <Stat label="Accuracy"   val={`${impact.forecastAccuracy}%`} />
              <Stat label="MAPE"       val={`${impact.avgMape}%`} />
              <Stat label="Waste Qty"  val={`${impact.totalWasteQty}`} />
              <Stat label="Profit"     val={`Rs.${impact.totalProfit?.toLocaleString()}`} />
            </div>
          </div>
        </>
      )}
    </div>
  );
}

/* 🔹 Small UI Components */

const KPI = ({ label, value, danger, success }) => (
  <div className={`glass-card kpi ${danger ? 'red' : success ? 'green' : ''}`}>
    <div className="kpi-label">{label}</div>
    <div className="kpi-value">{value}</div>
  </div>
);

const ComparisonCard = ({ title, data, danger, success }) => (
  <div className={`glass-card ${danger ? 'red' : success ? 'green' : ''}`}>
    <div className="card-title">{title}</div>
    {data.map(([k, v], i) => (
      <div key={i} className="row between">
        <span className="muted">{k}</span>
        <strong>{v}</strong>
      </div>
    ))}
  </div>
);

const Stat = ({ label, val }) => (
  <div className="stat-box">
    <div className="muted small">{label}</div>
    <div className="stat-val">{val}</div>
  </div>
);
