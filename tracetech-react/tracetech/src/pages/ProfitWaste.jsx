import React, { useState, useEffect } from 'react';
import { getImpactSummary } from '../api/api';
import { Loading, LineChartWidget, HBarChartWidget } from '../components/Shared';

export default function ProfitWaste() {
  const [impact, setImpact] = useState(null);
  const [from, setFrom]     = useState(() => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2,'0')}-01`;
  });
  const [to, setTo]         = useState(() => new Date().toISOString().slice(0,10));
  const [loading, setLoading] = useState(true);

  const fetchData = () => {
    setLoading(true);
    getImpactSummary(from, to)
      .then((d) => { setImpact(d); setLoading(false); })
      .catch(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, []); // eslint-disable-line

  const chartLabels  = ['Week 1', 'Week 2', 'Week 3', 'Week 4'];
  const totalWaste   = impact?.totalWasteInr || 0;
  const totalBaseline = totalWaste + (impact?.vsBaselineInr || 0);
  const wasteData    = [totalWaste * 0.20, totalWaste * 0.25, totalWaste * 0.30, totalWaste * 0.25].map(Math.round);
  const baselineData = [totalBaseline * 0.20, totalBaseline * 0.25, totalBaseline * 0.30, totalBaseline * 0.25].map(Math.round);

  const stats = impact ? [
    { label: 'Total days tracked',    val: impact.totalDays },
    { label: 'Average daily waste',   val: `₹${impact.avgDailyWasteInr}` },
    { label: 'Average daily revenue', val: `₹${impact.avgDailyRevenue?.toLocaleString()}` },
    { label: 'Forecast accuracy',     val: `${impact.forecastAccuracy}%` },
    { label: 'Average MAPE',          val: `${impact.avgMape}%` },
    { label: 'Total waste qty',       val: `${impact.totalWasteQty} units` },
    { label: 'Total profit',          val: `₹${impact.totalProfit?.toLocaleString()}` },
  ] : [];

  return (
    <div className="content">
      {/* ── Date Range Filter ── */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20, alignItems: 'center', flexWrap: 'wrap' }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)' }}>Date range:</div>
        <input
          type="date" value={from}
          onChange={(e) => setFrom(e.target.value)}
          className="form-input" style={{ width: 160, padding: '7px 10px' }}
        />
        <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>to</span>
        <input
          type="date" value={to}
          onChange={(e) => setTo(e.target.value)}
          className="form-input" style={{ width: 160, padding: '7px 10px' }}
        />
        <button className="btn btn-primary" onClick={fetchData}>Apply</button>
      </div>

      {loading ? (
        <Loading text="Loading analytics…" />
      ) : !impact ? (
        <div className="empty">No data available for this period. Submit some sales first.</div>
      ) : (
        <>
          {/* ── KPI Cards ── */}
          <div className="kpi-grid">
            <div className="kpi-card green">
              <div className="kpi-label">Total Revenue</div>
              <div className="kpi-value">₹{((impact.totalRevenue || 0) / 1000).toFixed(1)}k</div>
              <div className="kpi-sub">this period</div>
            </div>
            <div className="kpi-card red">
              <div className="kpi-label">Total Waste Cost</div>
              <div className="kpi-value">₹{(impact.totalWasteInr || 0).toLocaleString()}</div>
              <div className="kpi-sub">reduced by {impact.wasteReductionPct || 0}%</div>
            </div>
            <div className="kpi-card orange">
              <div className="kpi-label">Waste Reduction</div>
              <div className="kpi-value">{impact.wasteReductionPct || 0}%</div>
              <div className="kpi-sub">vs baseline method</div>
            </div>
            <div className="kpi-card blue">
              <div className="kpi-label">Saved vs Baseline</div>
              <div className="kpi-value">₹{(impact.vsBaselineInr || 0).toLocaleString()}</div>
              <div className="kpi-sub">AI advantage</div>
            </div>
          </div>

          {/* ── Charts ── */}
          <div className="charts-row">
            <div className="card">
              <div className="card-title">Waste vs Baseline</div>
              <LineChartWidget
                labels={chartLabels}
                datasets={[
                  {
                    label: 'TraceTech Waste',
                    data: wasteData,
                    borderColor: '#10b981',
                    backgroundColor: 'rgba(16,185,129,0.08)',
                    tension: 0.4, fill: true, pointRadius: 3,
                  },
                  {
                    label: 'Without TraceTech',
                    data: baselineData,
                    borderColor: '#ef4444',
                    borderDash: [5, 5],
                    backgroundColor: 'transparent',
                    tension: 0.4, fill: false, pointRadius: 2,
                  },
                ]}
                height={200}
              />
            </div>
            <div className="card">
              <div className="card-title">Top 5 wasted items</div>
              <HBarChartWidget
                labels={impact.topWastedItems?.map((t) => t.itemName) || []}
                data={impact.topWastedItems?.map((t) => t.totalWasteCost) || []}
                height={200}
              />
            </div>
          </div>

          {/* ── Summary Stats ── */}
          <div className="card">
            <div className="card-title">Summary Statistics</div>
            {stats.map((s, i) => (
              <div className="stat-row" key={i}>
                <span className="stat-label">{s.label}</span>
                <span className="stat-value mono">{s.val}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
